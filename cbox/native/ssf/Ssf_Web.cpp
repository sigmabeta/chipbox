// WASM-target wrapper for the SSF/DSF emulator. The Sega Saturn / Dreamcast PSF formats use
// psflib's loader chain, which calls back into `chipbox_psf_io.cpp`'s `fopen`/`fread` callbacks.
// On the browser those `fopen` calls hit Emscripten's MEMFS — the Kotlin side
// (`WasmSsfEmulator`) writes the staged main file and every chain-file `_lib*` sibling into
// MEMFS at the same paths before calling `chipbox_ssf_load_file`, so the existing C-side
// loadFile() / generateBuffer() in `Ssf.cpp` work unchanged.

#include <emscripten.h>
#include "Ssf.h"

extern "C" {

EMSCRIPTEN_KEEPALIVE
int chipbox_ssf_load_file(const char* path) {
    loadFile(path);
    const char* err = get_last_error();
    return err ? 1 : 0;
}

EMSCRIPTEN_KEEPALIVE
int chipbox_ssf_play(int16_t* target, int frames) {
    return generateBuffer(target, frames);
}

EMSCRIPTEN_KEEPALIVE
int chipbox_ssf_sample_rate() { return get_sample_rate(); }

EMSCRIPTEN_KEEPALIVE
const char* chipbox_ssf_last_error() {
    const char* err = get_last_error();
    return err ? err : "";
}

EMSCRIPTEN_KEEPALIVE
void chipbox_ssf_teardown() { teardown(); }

}  // extern "C"
