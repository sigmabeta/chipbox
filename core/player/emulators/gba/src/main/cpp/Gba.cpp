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
    m_output.stream.postAudioBuffer = _gsf_postAudioBuffer;

    core->init(core);
    core->setAVStream(core, &m_output.stream);
    mCoreInitConfig(core, NULL);

    unsigned int sample_rate = 44100;

    int32_t core_frequency = core->frequency(core);
    blip_set_rates(core->getAudioChannel(core, 0), core_frequency, sample_rate);
    blip_set_rates(core->getAudioChannel(core, 1), core_frequency, sample_rate);

    struct mCoreOptions opts = {};
    opts.useBios = false;
    opts.skipBios = true;
    opts.volume = 0x100;
    opts.sampleRate = sample_rate;

    core->loadROM(core, rom);
    core->reset(core);

    m_core = core;
}

int32_t generateBuffer(int16_t *target_array, int32_t buffer_size_frames) {
    uint16_t samples_written = 0;

    if (m_output.buffer_size_frames != buffer_size_frames) {
        delete m_output.samples;
        m_core->setAudioBufferSize(m_core, buffer_size_frames);

        m_output.buffer_size_frames = buffer_size_frames;
        m_output.samples = static_cast<int16_t *>(malloc(buffer_size_frames * 4));
    }

    m_output.frames_available = 0;
    while (!m_output.frames_available) {
//        printf("Running frame");
        m_core->runFrame(m_core);
    }
    printf("Frames available: %d / %d", m_output.frames_available, buffer_size_frames);

    samples_written = m_output.frames_available;

    memcpy(target_array, m_output.samples, buffer_size_frames * 4);

    int framesWritten = samples_written / 2;
    return framesWritten;
}

void teardown() {
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

static void _gsf_postAudioBuffer(struct mAVStream *stream, blip_t *left, blip_t *right) {
    struct gsf_running_state *state = (struct gsf_running_state *) stream;
    blip_read_samples(left, state->samples, m_output.buffer_size_frames, true);
    blip_read_samples(right, state->samples + 1, m_output.buffer_size_frames, true);
    state->frames_available += m_output.buffer_size_frames;
}

