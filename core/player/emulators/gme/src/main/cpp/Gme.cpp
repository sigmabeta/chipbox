#include "Gme.h"
#include "gme/Music_Emu.h"

const char *last_error;

Music_Emu *g_emu;

const char *g_last_error;

void loadFile(const char *filename_c_str) {
    if (g_emu) {
        delete g_emu;
        g_emu = nullptr;
    }

    // Determine file type.
    gme_type_t file_type;
    g_last_error = gme_identify_file(filename_c_str, &file_type);

    if (g_last_error) {
        return;
    }

    if (!file_type) {
        g_last_error = "Unsupported music type";
        return;
    }

    g_emu = file_type->new_emu();

    if (!g_emu) {
        g_last_error = "Out of memory";
        return;
    }

    if (g_last_error) {
        return;
    }

    g_emu->set_sample_rate(44100);
    g_last_error = g_emu->load_file(filename_c_str);

    if (g_last_error) {
        return;
    }


    // TODO This needs to be passed in.
    g_last_error = g_emu->start_track(0);

    if (g_last_error) {
        return;
    }
}

int32_t generateBuffer(int16_t *target_array, int32_t buffer_size_shorts) {
    g_last_error = gme_play(g_emu, buffer_size_shorts * 2, target_array);
    return buffer_size_shorts / 2;
}

void teardown() {
    if (g_emu != nullptr) {
        delete g_emu;
        g_emu = nullptr;
    }
}

const char *get_last_error() {
    return last_error;
}

int32_t get_sample_rate() {
    return g_emu->sample_rate();
}