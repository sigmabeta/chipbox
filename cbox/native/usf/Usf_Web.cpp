// WASM-target wrapper for the USF (Nintendo 64) emulator. The Emscripten.Makefile in this
// directory has long built lazyusf2 for the web (chip-player-js still uses it), and the
// CMakeLists.txt is already configured for the same cached-interpreter source list — only the
// JNI surface (`kotlin-jni.cpp`) is Android-specific.
//
// The cached-interpreter is roughly 10x slower than x86_64 dynarec, but Emscripten can't run
// x86 recompilers and the new_dynarec path needs JIT-eligible host memory. Audio rendering is
// still well under realtime for the USF library — chip-player-js demonstrates that at scale.
//
// psflib's chain-file loader (the `_lib.usf` siblings) `fopen`s into Emscripten's MEMFS — the
// Kotlin layer (`WasmUsfEmulator`) mirrors the staged track dir into MEMFS before calling
// `chipbox_usf_load_file`.

#include <emscripten.h>
#include "Usf.h"

extern "C" {

EMSCRIPTEN_KEEPALIVE
int chipbox_usf_load_file(const char* path) {
    loadFile(path);
    const char* err = get_last_error();
    return err ? 1 : 0;
}

EMSCRIPTEN_KEEPALIVE
int chipbox_usf_play(int16_t* target, int frames) {
    return generateBuffer(target, frames);
}

EMSCRIPTEN_KEEPALIVE
int chipbox_usf_sample_rate() { return get_sample_rate(); }

EMSCRIPTEN_KEEPALIVE
const char* chipbox_usf_last_error() {
    const char* err = get_last_error();
    return err ? err : "";
}

EMSCRIPTEN_KEEPALIVE
void chipbox_usf_teardown() { teardown(); }

}  // extern "C"
