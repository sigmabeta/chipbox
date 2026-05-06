#include "2sf.h"
#include "common/common.h"

const char *last_error;

void loadFile(const char *filename_c_str) {

    char * not_const_filename = const_cast<char *>(filename_c_str);
    int result = xsf_start(not_const_filename);

    if (result == -1) {
        last_error = "Not a valid 2SF file.";
    } else if (result == -2) {
        last_error = "Out of memory?!";
    } else {
        last_error = nullptr;
    }
}

int32_t generateBuffer(int16_t *target_array, int32_t output_size_frames) {
    int frames_generated = xsf_gen(target_array, output_size_frames);

    if (frames_generated <= 0) {
        last_error = "Failed to generate any audio.";
        return 0;
    }

    return frames_generated;
}

void teardown() {
    xsf_term();
}

const char *get_last_error() {
    return last_error;
}

int32_t get_sample_rate() {
    return 44100;
}

