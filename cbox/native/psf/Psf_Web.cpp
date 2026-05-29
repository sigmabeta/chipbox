// WASM-target wrapper for the slopsf (PSF1/PSF2 HLE) emulator. Both PSF1 (PlayStation) and
// PSF2 (PlayStation 2) tracks boot through the HLE IOP-kernel path — no copyrighted BIOS
// blob required. psflib's chain-file loader reads `_lib*.psf` / `_lib*.psf2` siblings via
// `fopen`, which resolves against Emscripten MEMFS (`WasmPsfEmulator` pre-populates it).

#include <emscripten.h>
#include "Psf.h"

extern "C" {

EMSCRIPTEN_KEEPALIVE
int chipbox_psf_load_file(const char* path) {
    loadFile(path);
    const char* err = get_last_error();
    return err ? 1 : 0;
}

EMSCRIPTEN_KEEPALIVE
int chipbox_psf_play(int16_t* target, int frames) {
    return generateBuffer(target, frames);
}

EMSCRIPTEN_KEEPALIVE
int chipbox_psf_sample_rate() { return get_sample_rate(); }

EMSCRIPTEN_KEEPALIVE
const char* chipbox_psf_last_error() {
    const char* err = get_last_error();
    return err ? err : "";
}

EMSCRIPTEN_KEEPALIVE
void chipbox_psf_teardown() { teardown(); }

}  // extern "C"
