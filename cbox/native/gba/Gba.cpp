#include "Gba.h"

#include <cstdarg>

#include <mgba/core/log.h>

static const char *last_error = nullptr;

struct mCore *m_core;

gsf_loader_state m_rom;

struct gsf_running_state m_output;

// --- GBA log shim: silence trace spam, detect a derailed CPU -----------------
//
// mGBA logs prolifically: per-frame "GBA DMA: Starting DMA ..." (INFO) and
// "GBA BIOS: SWI: ..." (DEBUG) lines flood the log during normal playback. The
// shim below drops everything below WARN so only real warnings and errors get
// through.
//
// It also watches for the derail signature: a corrupt or unsupported GSF ROM
// sends the emulated ARM core off into unmapped memory, where mGBA reads its
// 0xE710B710 sentinel and raises an undefined-instruction exception on every
// step -- GBAIllegal logs "Illegal opcode: %08x" (WARN) and the track renders
// pure silence. A healthy track hits zero of these; a derail hits ~10^5/s. We
// count them (capping the forwarded copies) so generateBuffer can fail the
// track via last_error once a buffer is both silent and saw one.

static long illegal_opcode_count = 0;
static struct mLogger *prev_logger = nullptr;

// Forward only the first handful of illegal-opcode lines to the underlying
// logger; the rest are pure noise (and there can be millions).
static const long kIllegalOpcodeLogLimit = 8;

static void chipbox_gba_log(struct mLogger *logger, int category,
                            enum mLogLevel level, const char *format,
                            va_list args) {
    // Count every illegal opcode (before any drop) so the derail detector in
    // generateBuffer sees the full rate.
    const bool illegal = format && strncmp(format, "Illegal opcode", 14) == 0;
    if (illegal) {
        ++illegal_opcode_count;
    }

    // Drop mGBA's per-frame trace spam: only WARN and worse (lower enum value =
    // higher severity) reach the underlying logger.
    if (level > mLOG_WARN) {
        return;
    }

    // Cap the illegal-opcode warnings too -- on a derail there can be millions.
    if (illegal && illegal_opcode_count > kIllegalOpcodeLogLimit) {
        return;
    }

    if (prev_logger && prev_logger->log && prev_logger != logger) {
        prev_logger->log(prev_logger, category, level, format, args);
    }
}

static struct mLogger chipbox_gba_logger = { chipbox_gba_log, nullptr };

// Install our counting log shim once, chaining to whatever logger was already
// active (and reusing its filter, so log volume is unchanged) -- existing
// routing such as Android logcat is preserved across tracks.
static void install_log_shim() {
    if (mLogGetContext() != &chipbox_gba_logger) {
        prev_logger = mLogGetContext();
        chipbox_gba_logger.filter = prev_logger ? prev_logger->filter : nullptr;
        mLogSetDefaultLogger(&chipbox_gba_logger);
    }
}

// True if every sample in the just-rendered buffer is zero. Paired with the
// illegal-opcode signal, this distinguishes a derailed (silent) track from a
// healthy one that merely tripped -- and recovered from -- a stray opcode.
static bool is_silent(const int16_t *interleaved, int32_t frames) {
    for (int32_t i = 0; i < frames * 2; ++i) {
        if (interleaved[i] != 0) {
            return false;
        }
    }
    return true;
}

void loadFile(const char *filename_c_str) {
    teardown();

    // Clear any error carried over from a previous track and arm the
    // illegal-opcode-storm watch for this one.
    last_error = nullptr;
    illegal_opcode_count = 0;
    install_log_shim();

    if (!m_rom.data) {
        int ret = psf_load(
                filename_c_str,
                &psf_file_system,
                0x22,
                gsf_loader,
                &m_rom,
                0,
                0,
                0,
                0,
                0
        );

        if (ret < 0) {
            last_error = "Invalid GSF";
            return;
        }

        if (m_rom.data_size > UINT_MAX) {
            last_error = "Invalid GSF";
            return;
        }
    }

    struct VFile *rom = VFileFromConstMemory(m_rom.data, m_rom.data_size);
    if (!rom) {
        last_error = "Bad allocation.";
        return;
    }

    struct mCore *core = mCoreFindVF(rom);
    if (!core) {
        rom->close(rom);
        last_error = "Invalid GSF";
        return;
    }

    memset(&m_output, 0, sizeof(m_output));

    core->init(core);
    mCoreInitConfig(core, NULL);

    struct mCoreOptions opts = {};
    opts.useBios = false;
    opts.skipBios = true;
    opts.volume = 0x100;

    core->loadROM(core, rom);
    core->reset(core);

    m_core = core;
}

int32_t generateBuffer(int16_t *target_array, int32_t buffer_size_frames) {

    if (!m_output.audio_inited || m_output.buffer_size_frames != buffer_size_frames) {
        if (m_output.audio_inited) {
            mAudioResamplerDeinit(&m_output.resampler);
            mAudioBufferDeinit(&m_output.resampled);
        }

        // GBA's internal audio buffer is hard-capped at 0x4000 frames upstream.
        size_t core_buffer = buffer_size_frames > 0x4000 ? 0x4000 : buffer_size_frames;
        m_core->setAudioBufferSize(m_core, core_buffer);

        // Destination is fixed 44100 Hz stereo, with 2x headroom so one
        // resample pass can overshoot a request without stalling the loop.
        mAudioBufferInit(&m_output.resampled, buffer_size_frames * 2, 2);
        mAudioResamplerInit(&m_output.resampler, mINTERPOLATOR_SINC);
        mAudioResamplerSetDestination(&m_output.resampler, &m_output.resampled, 44100.0);

        m_output.buffer_size_frames = buffer_size_frames;
        m_output.audio_inited = true;
    }

    struct mAudioBuffer *src = m_core->getAudioBuffer(m_core);

    const long illegal_before = illegal_opcode_count;

    while ((int32_t) mAudioBufferAvailable(&m_output.resampled) < buffer_size_frames) {
        m_core->runFrame(m_core);
        // Re-query the source rate every pass: the GSF driver can reprogram
        // SOUNDBIAS at any time, and the resampler adapts on the fly.
        mAudioResamplerSetSource(&m_output.resampler, src,
                                 m_core->audioSampleRate(m_core), true);
        mAudioResamplerProcess(&m_output.resampler);
    }

    int32_t frames = mAudioBufferRead(&m_output.resampled, target_array,
                                      buffer_size_frames);

    // An illegal opcode means the CPU executed something that isn't an
    // instruction -- in practice a corrupt or unsupported ROM that has run off
    // into junk memory. Fail as soon as it happens, but only when this buffer
    // also came out silent: a healthy driver that trips a stray undefined
    // opcode recovers via the BIOS handler and keeps producing audio, and we
    // won't kill a track that's actually playing. A derailed ROM hits illegal
    // opcodes AND emits silence every buffer, so this catches it on the first
    // one. Returning 0 frames matches the PSF wrapper's on-error convention.
    if (!last_error && illegal_opcode_count > illegal_before &&
        is_silent(target_array, frames)) {
        last_error =
                "GSF file appears corrupt or unsupported: the emulated GBA CPU "
                "hit an illegal opcode and produced no audio.";
        return 0;
    }

    return frames;
}

void teardown() {
    illegal_opcode_count = 0;

    if (m_output.audio_inited) {
        mAudioResamplerDeinit(&m_output.resampler);
        mAudioBufferDeinit(&m_output.resampled);
        m_output.audio_inited = false;
    }

    if (m_core) {
        m_core->deinit(m_core);
        m_core = NULL;
    }

    delete m_rom.data;
    m_rom.data = nullptr;
    m_rom.data_size = 0;
}

const char *get_last_error() {
    return last_error;
}

int32_t get_sample_rate() {
    // generateBuffer() resamples the core's native (and possibly varying)
    // output to a fixed 44100 Hz, so this is constant and safe to read
    // before any frames are generated.
    return 44100;
}

inline unsigned get_le32(void const *p) {
    return (unsigned) ((unsigned char const *) p)[3] << 24 |
           (unsigned) ((unsigned char const *) p)[2] << 16 |
           (unsigned) ((unsigned char const *) p)[1] << 8 |
           (unsigned) ((unsigned char const *) p)[0];
}

int gsf_loader(void *context, const uint8_t *exe, size_t exe_size,
               const uint8_t *reserved, size_t reserved_size) {
    if (exe_size < 12) return -1;

    struct gsf_loader_state *state = (struct gsf_loader_state *) context;

    unsigned char *iptr;
    unsigned isize;
    unsigned char *xptr;
    unsigned xentry = get_le32(exe + 0);
    unsigned xsize = get_le32(exe + 8);
    unsigned xofs = get_le32(exe + 4) & 0x1ffffff;
    if (xsize < exe_size - 12) return -1;
    if (!state->entry_set) {
        state->entry = xentry;
        state->entry_set = 1;
    }
    {
        iptr = state->data;
        isize = state->data_size;
        state->data = 0;
        state->data_size = 0;
    }
    if (!iptr) {
        unsigned rsize = xofs + xsize;
        {
            rsize -= 1;
            rsize |= rsize >> 1;
            rsize |= rsize >> 2;
            rsize |= rsize >> 4;
            rsize |= rsize >> 8;
            rsize |= rsize >> 16;
            rsize += 1;
        }
        iptr = (unsigned char *) malloc(rsize + 10);
        if (!iptr)
            return -1;
        memset(iptr, 0, rsize + 10);
        isize = rsize;
    } else if (isize < xofs + xsize) {
        unsigned rsize = xofs + xsize;
        {
            rsize -= 1;
            rsize |= rsize >> 1;
            rsize |= rsize >> 2;
            rsize |= rsize >> 4;
            rsize |= rsize >> 8;
            rsize |= rsize >> 16;
            rsize += 1;
        }
        xptr = (unsigned char *) realloc(iptr, xofs + rsize + 10);
        if (!xptr) {
            free(iptr);
            return -1;
        }
        iptr = xptr;
        isize = rsize;
    }
    memcpy(iptr + xofs, exe + 12, xsize);
    {
        state->data = iptr;
        state->data_size = isize;
    }
    return 0;
}

