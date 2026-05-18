#include "Gba.h"

char *last_error;

struct mCore *m_core;

gsf_loader_state m_rom;

struct gsf_running_state m_output;

void loadFile(const char *filename_c_str) {
    teardown();

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

    while ((int32_t) mAudioBufferAvailable(&m_output.resampled) < buffer_size_frames) {
        m_core->runFrame(m_core);
        // Re-query the source rate every pass: the GSF driver can reprogram
        // SOUNDBIAS at any time, and the resampler adapts on the fly.
        mAudioResamplerSetSource(&m_output.resampler, src,
                                 m_core->audioSampleRate(m_core), true);
        mAudioResamplerProcess(&m_output.resampler);
    }

    return mAudioBufferRead(&m_output.resampled, target_array, buffer_size_frames);
}

void teardown() {
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

