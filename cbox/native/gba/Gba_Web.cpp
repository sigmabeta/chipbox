// WASM-target wrapper for the GSF (GBA Sound Format) emulator. Backed by mgba — the same
// full GBA core that runs the standalone player. psflib's chain-file loader resolves through
// Emscripten MEMFS once `WasmGbaEmulator` mirrors the staged track dir into it.

#include <emscripten.h>
#include "Gba.h"

extern "C" {

EMSCRIPTEN_KEEPALIVE
int chipbox_gba_load_file(const char* path) {
    loadFile(path);
    const char* err = get_last_error();
    return err ? 1 : 0;
}

EMSCRIPTEN_KEEPALIVE
int chipbox_gba_play(int16_t* target, int frames) {
    return generateBuffer(target, frames);
}

EMSCRIPTEN_KEEPALIVE
int chipbox_gba_sample_rate() { return get_sample_rate(); }

EMSCRIPTEN_KEEPALIVE
const char* chipbox_gba_last_error() {
    const char* err = get_last_error();
    return err ? err : "";
}

EMSCRIPTEN_KEEPALIVE
void chipbox_gba_teardown() { teardown(); }

}  // extern "C"
