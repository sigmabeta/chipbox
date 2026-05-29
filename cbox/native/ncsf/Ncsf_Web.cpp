// WASM-target wrapper for the NCSF (NDS Nitro Composer) emulator. PSF-family loader chain via
// psflib, same MEMFS bridging as PSF/SSF/USF — the Kotlin layer mirrors the staged track dir
// into Emscripten's MEMFS before calling chipbox_ncsf_load_file.

#include <emscripten.h>
#include "ncsf.h"

extern "C" {

EMSCRIPTEN_KEEPALIVE
int chipbox_ncsf_load_file(const char* path) {
    loadFile(path);
    const char* err = get_last_error();
    return err ? 1 : 0;
}

EMSCRIPTEN_KEEPALIVE
int chipbox_ncsf_play(int16_t* target, int frames) {
    return generateBuffer(target, frames);
}

EMSCRIPTEN_KEEPALIVE
int chipbox_ncsf_sample_rate() { return get_sample_rate(); }

EMSCRIPTEN_KEEPALIVE
const char* chipbox_ncsf_last_error() {
    const char* err = get_last_error();
    return err ? err : "";
}

EMSCRIPTEN_KEEPALIVE
void chipbox_ncsf_teardown() { teardown(); }

}  // extern "C"
