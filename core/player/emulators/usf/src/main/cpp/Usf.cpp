#include "Usf.h"

char *last_error;

void loadFile(const char *filename_c_str) {
//    teardown();
//
//    if (!m_rom.data) {
//        int ret = psf_load(
//                filename_c_str,
//                &psf_file_system,
//                0x22,
//                gsf_loader,
//                &m_rom,
//                0,
//                0,
//                0,
//                0,
//                0
//        );
//
//        if (ret < 0) {
//            last_error = "Invalid GSF";
//            return;
//        }
//
//        if (m_rom.data_size > UINT_MAX) {
//            last_error = "Invalid GSF";
//            return;
//        }
//    }
//
//    struct VFile *rom = VFileFromConstMemory(m_rom.data, m_rom.data_size);
//    if (!rom) {
//        last_error = "Bad allocation.";
//        return;
//    }
//
//    struct mCore *core = mCoreFindVF(rom);
//    if (!core) {
//        rom->close(rom);
//        last_error = "Invalid GSF";
//        return;
//    }
//
//    memset(&m_output, 0, sizeof(m_output));
//    m_output.stream.postAudioBuffer = _gsf_postAudioBuffer;
//
//    core->init(core);
//    core->setAVStream(core, &m_output.stream);
//    mCoreInitConfig(core, NULL);
//
//    unsigned int sample_rate = 44100;
//
//    int32_t core_frequency = core->frequency(core);
//    blip_set_rates(core->getAudioChannel(core, 0), core_frequency, sample_rate);
//    blip_set_rates(core->getAudioChannel(core, 1), core_frequency, sample_rate);
//
//    struct mCoreOptions opts = {};
//    opts.useBios = false;
//    opts.skipBios = true;
//    opts.volume = 0x100;
//    opts.sampleRate = sample_rate;
//
//    mCoreConfigSetIntValue(&core->config, "gba.audioHle", 1);
//
//    core->loadROM(core, rom);
//    core->reset(core);
//
//    m_core = core;
}

int32_t generateBuffer(int16_t *target_array, int32_t buffer_size_frames) {
//    uint16_t samples_written = 0;
//
//    if (m_output.buffer_size_frames != buffer_size_frames) {
//        delete m_output.samples;
//        m_core->setAudioBufferSize(m_core, buffer_size_frames);
//
//        m_output.buffer_size_frames = buffer_size_frames;
//        m_output.samples = static_cast<int16_t *>(malloc(buffer_size_frames * 4));
//    }
//
//    m_output.frames_available = 0;
//    while (!m_output.frames_available) {
////        printf("Running frame");
//        m_core->runFrame(m_core);
//    }
//    printf("Frames available: %d / %d", m_output.frames_available, buffer_size_frames);
//
//    samples_written = m_output.frames_available;
//
//    memcpy(target_array, m_output.samples, buffer_size_frames * 4);
//
//    int framesWritten = samples_written / 2;
//    return framesWritten;
return 0;
}

void teardown() {
//    if (m_core) {
//        m_core->deinit(m_core);
//        m_core = NULL;
//    }
//
//    delete m_rom.data;
//    m_rom.data = nullptr;
//    m_rom.data_size = 0;
}

const char *get_last_error() {
    return last_error;
}

int32_t get_sample_rate() {
    return 44100;
}
