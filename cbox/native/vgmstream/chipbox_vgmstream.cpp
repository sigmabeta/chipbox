#include "chipbox_vgmstream.h"

extern "C" {
#include "libvgmstream.h"
}

#include <cstring>
#include <vector>

namespace {

// vgmstream decodes "streamed" (prerecorded) game audio — hundreds of PCM/ADPCM formats (ADX,
// HCA, DSP/BRSTM, STRM, FSB, ...). We drive the high-level libvgmstream API: open by path, force
// stereo 16-bit output, and pull PCM. Looping game music is rendered as N loops + a fade, matching
// the length the scanner computes from the same config so chipbox's frame budget lines up.
const double LOOP_COUNT = 2.0;
const double FADE_TIME = 10.0;

libvgmstream_t *g_lib = nullptr;
int g_channels = 0;
int32_t g_sample_rate = 0;
std::vector<int16_t> g_mono_buffer; // scratch for up-mixing mono sources to stereo
const char *g_last_error = nullptr;

void teardown_internal() {
    if (g_lib) {
        libvgmstream_free(g_lib);
        g_lib = nullptr;
    }
    g_channels = 0;
    g_sample_rate = 0;
}

} // namespace

void loadFile(const char *path, int32_t subsong) {
    teardown_internal();
    g_last_error = nullptr;

    // vgmstream logs format notes to stdout by default; silence it (Android logcat / desktop noise).
    libvgmstream_set_log(LIBVGMSTREAM_LOG_LEVEL_NONE, nullptr);

    libstreamfile_t *sf = libstreamfile_open_from_stdio(path);
    if (!sf) {
        g_last_error = "Could not open file.";
        return;
    }

    libvgmstream_config_t cfg = {};
    cfg.loop_count = LOOP_COUNT;
    cfg.fade_time = FADE_TIME;
    cfg.auto_downmix_channels = 2;        // fold >2ch down to stereo (chipbox is stereo-only)
    cfg.force_sfmt = LIBVGMSTREAM_SFMT_PCM16;

    const int sub = subsong > 0 ? subsong : 1; // 1..N, 0 = first
    g_lib = libvgmstream_create(sf, sub, &cfg);
    libstreamfile_close(sf); // vgmstream re-opens internally as needed

    if (!g_lib) {
        g_last_error = "Unsupported or invalid file.";
        return;
    }

    g_channels = g_lib->format->channels;
    g_sample_rate = g_lib->format->sample_rate;
    if (g_channels < 1 || g_sample_rate <= 0) {
        g_last_error = "File has no decodable audio.";
        teardown_internal();
    }
}

int32_t generateBuffer(int16_t *target_array, int32_t output_size_frames) {
    if (!g_lib) {
        g_last_error = "Cannot generate audio: emulator not loaded.";
        return 0;
    }

    if (g_channels == 2) {
        // buf must hold channels * sample_size * frames = 2 * 2 * frames bytes = target as-is.
        if (libvgmstream_fill(g_lib, target_array, output_size_frames) < 0) {
            g_last_error = "Decode error.";
            return 0;
        }
    } else if (g_channels == 1) {
        g_mono_buffer.resize(output_size_frames);
        if (libvgmstream_fill(g_lib, g_mono_buffer.data(), output_size_frames) < 0) {
            g_last_error = "Decode error.";
            return 0;
        }
        for (int32_t i = 0; i < output_size_frames; ++i) {
            const int16_t s = g_mono_buffer[i];
            target_array[i * 2] = s;
            target_array[i * 2 + 1] = s;
        }
    } else {
        // Shouldn't happen (downmix forces <=2), but never hand back uninitialised memory.
        memset(target_array, 0, static_cast<size_t>(output_size_frames) * 2 * sizeof(int16_t));
    }

    // Always report a full buffer (vgmstream zero-fills past EOF); chipbox's per-track frame
    // budget — derived from the scanned length — is what actually ends the track.
    return output_size_frames;
}

void teardown() {
    teardown_internal();
}

const char *get_last_error() {
    return g_last_error;
}

int32_t get_sample_rate() {
    return g_sample_rate;
}

const char *const *get_supported_extensions(int *count) {
    return libvgmstream_get_extensions(count);
}

const char *const *get_common_extensions(int *count) {
    return libvgmstream_get_common_extensions(count);
}
