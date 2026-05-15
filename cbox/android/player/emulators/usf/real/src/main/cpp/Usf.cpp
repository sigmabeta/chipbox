#include "Usf.h"
#include "common.h"
#include "usf/usf.h"

#include <cstdlib>
#include <cstring>
#include <strings.h>

namespace {

struct usf_loader_state {
    void *emu_state;
    int enable_compare;
    int enable_fifo_full;
};

void *g_state = nullptr;
int32_t g_sample_rate = 0;
const char *g_last_error = nullptr;

void teardown_internal() {
    if (g_state != nullptr) {
        usf_shutdown(g_state);
        free(g_state);
        g_state = nullptr;
    }
    g_sample_rate = 0;
}

int usf_loader(void *context, const uint8_t *exe, size_t exe_size,
               const uint8_t *reserved, size_t reserved_size) {
    auto *state = static_cast<usf_loader_state *>(context);
    if (exe_size > 0) {
        return -1;
    }
    return usf_upload_section(state->emu_state, reserved, reserved_size);
}

int usf_info(void *context, const char *name, const char *value) {
    auto *state = static_cast<usf_loader_state *>(context);
    if (strcasecmp(name, "_enablecompare") == 0 && strlen(value)) {
        state->enable_compare = 1;
    } else if (strcasecmp(name, "_enablefifofull") == 0 && strlen(value)) {
        state->enable_fifo_full = 1;
    }
    return 0;
}

} // namespace

void loadFile(const char *path) {
    teardown_internal();
    g_last_error = nullptr;

    g_state = malloc(usf_get_state_size());
    if (g_state == nullptr) {
        g_last_error = "Failed to allocate USF state.";
        return;
    }
    usf_clear(g_state);

    // Cycle-accurate (LLE) RSP audio for best compatibility.
    usf_set_hle_audio(g_state, 0);

    usf_loader_state loader{};
    loader.emu_state = g_state;

    if (psf_load(path, &psf_file_system, 0x21,
                 usf_loader, &loader,
                 usf_info, &loader,
                 1, nullptr, nullptr) < 0) {
        g_last_error = "Invalid USF file";
        teardown_internal();
        return;
    }

    usf_set_compare(g_state, loader.enable_compare);
    usf_set_fifo_full(g_state, loader.enable_fifo_full);

    // Force one DMA block to determine the ROM's native sample rate.
    const char *err = usf_render(g_state, nullptr, 0, &g_sample_rate);
    if (err != nullptr) {
        g_last_error = err;
        teardown_internal();
    }
}

int32_t generateBuffer(int16_t *target, int32_t frames) {
    if (g_state == nullptr) {
        g_last_error = "Cannot generate audio: emulator not loaded.";
        return 0;
    }

    const char *err = usf_render(g_state, target, frames, &g_sample_rate);
    if (err != nullptr) {
        g_last_error = err;
        return 0;
    }
    return frames;
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
