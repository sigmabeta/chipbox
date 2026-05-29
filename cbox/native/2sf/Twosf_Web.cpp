// WASM-target wrapper for the 2SF (NDS Sound Format) emulator. Uses vio2sf — Desmume-derived
// NDS core via the PSF-family loader chain. Same MEMFS bridging as PSF/SSF/USF/NCSF.

#include <emscripten.h>
#include "2sf.h"

extern "C" {

EMSCRIPTEN_KEEPALIVE
int chipbox_twosf_load_file(const char* path) {
    loadFile(path);
    const char* err = get_last_error();
    return err ? 1 : 0;
}

EMSCRIPTEN_KEEPALIVE
int chipbox_twosf_play(int16_t* target, int frames) {
    return generateBuffer(target, frames);
}

EMSCRIPTEN_KEEPALIVE
int chipbox_twosf_sample_rate() { return get_sample_rate(); }

EMSCRIPTEN_KEEPALIVE
const char* chipbox_twosf_last_error() {
    const char* err = get_last_error();
    return err ? err : "";
}

EMSCRIPTEN_KEEPALIVE
void chipbox_twosf_teardown() { teardown(); }

}  // extern "C"
