#include "Gba.h"
#include "GbaOutput.h"

#include <algorithm>
#include <cstdarg>
#include <vector>

#include <mgba/core/log.h>

static const char *last_error = nullptr;

struct mCore *m_core;

gsf_loader_state m_rom;

static GbaOutput m_output;

// One runFrame's worth of core audio, copied out of mGBA's ring buffer.
static std::vector<int16_t> m_frame_audio;

// How much of the track loadFile() renders before it settles the output rate.
// GSF drivers set their mixing rate during init; across 100 games the last
// change landed 0.27 s in, so ~0.5 s (30 frames at 59.73 Hz) leaves headroom.
static const int kPrerollFrames = 30;

// The output rate is the driver's settled rate, capped here. Nearly every GSF
// settles at 65536 or 32768 Hz; the rare 131072/262144 Hz driver is resampled
// down so it doesn't cost 2-4x the cache space and speaker work.
static const int32_t kMaxOutputRate = 65536;

// What mGBA starts at, used if the pre-roll produced no audio at all.
static const int32_t kDefaultRate = 32768;

static const char *const kDerailError =
        "GSF file appears corrupt or unsupported: the emulated GBA CPU "
        "hit an illegal opcode and produced no audio.";

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

// Runs one GBA frame and moves the audio it made into m_frame_audio. Returns
// the frame count; *rate is the rate the core mixed at.
static size_t run_core_frame(int32_t *rate) {
    m_core->runFrame(m_core);
    // Read the rate after the frame: the driver can reprogram SOUNDBIAS at
    // any time, and this is the rate the frame's tail was mixed at.
    *rate = (int32_t) m_core->audioSampleRate(m_core);
    struct mAudioBuffer *src = m_core->getAudioBuffer(m_core);
    const size_t frames = mAudioBufferAvailable(src);
    m_frame_audio.resize(frames * GbaOutput::kChannels);
    return mAudioBufferRead(src, m_frame_audio.data(), frames);
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

    core->init(core);
    mCoreInitConfig(core, NULL);

    struct mCoreOptions opts = {};
    opts.useBios = false;
    opts.skipBios = true;
    opts.volume = 0x100;

    core->loadROM(core, rom);
    core->reset(core);

    m_core = core;

    // Pre-roll: render the driver's init so the output rate is the one it
    // settles on. The audio is kept and becomes the start of the track.
    struct Chunk {
        int32_t rate;
        size_t offset;
        size_t frames;
    };
    std::vector<int16_t> preroll;
    std::vector<Chunk> chunks;
    for (int i = 0; i < kPrerollFrames; ++i) {
        int32_t rate;
        const size_t frames = run_core_frame(&rate);
        if (frames == 0) {
            continue;
        }
        if (!chunks.empty() && chunks.back().rate == rate) {
            chunks.back().frames += frames;
        } else {
            chunks.push_back({rate, preroll.size() / GbaOutput::kChannels, frames});
        }
        preroll.insert(preroll.end(), m_frame_audio.begin(), m_frame_audio.end());
    }

    // A derailed ROM shows up here first: illegal opcodes and nothing but
    // silence for the whole pre-roll. See generateBuffer for the reasoning.
    if (illegal_opcode_count > 0 &&
        is_silent(preroll.data(), (int32_t) (preroll.size() / GbaOutput::kChannels))) {
        last_error = kDerailError;
        return;
    }

    const int32_t settled = chunks.empty() ? kDefaultRate : chunks.back().rate;
    m_output.start(std::min(settled, kMaxOutputRate));
    for (const Chunk &chunk : chunks) {
        m_output.feed(preroll.data() + chunk.offset * GbaOutput::kChannels,
                      chunk.frames, chunk.rate);
    }
}

int32_t generateBuffer(int16_t *target_array, int32_t buffer_size_frames) {
    const long illegal_before = illegal_opcode_count;

    while ((int32_t) m_output.queued() < buffer_size_frames) {
        int32_t rate;
        const size_t frames = run_core_frame(&rate);
        m_output.feed(m_frame_audio.data(), frames, rate);
    }

    int32_t frames = (int32_t) m_output.read(target_array, buffer_size_frames);

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
        last_error = kDerailError;
        return 0;
    }

    return frames;
}

void teardown() {
    illegal_opcode_count = 0;

    m_output.stop();

    if (m_core) {
        m_core->deinit(m_core);
        m_core = NULL;
    }

    // gsf_loader allocates with malloc/realloc, so it must be freed, not deleted.
    free(m_rom.data);
    m_rom.data = nullptr;
    m_rom.data_size = 0;
}

const char *get_last_error() {
    return last_error;
}

int32_t get_sample_rate() {
    // Fixed by loadFile()'s pre-roll for the whole track, so it's safe to read
    // before any frames are generated.
    return m_output.outputRate() != 0 ? m_output.outputRate() : kDefaultRate;
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

