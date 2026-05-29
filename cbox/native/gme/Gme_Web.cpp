// WASM-target wrapper for libgme. The host JNI/native shim (`kotlin-jni.cpp` + `Gme.cpp`) loads
// tracks via `gme_load_file` with a filesystem path; the browser has no filesystem, so this
// translation unit instead takes raw bytes from the Emscripten heap and uses `gme_open_data`.
//
// All entry points are flat C, marked `EMSCRIPTEN_KEEPALIVE` so emcc doesn't dead-strip them in
// `-Os`. The Kotlin/JS bindings call them via the Emscripten module's `_<name>` exports.

#include <emscripten.h>
#include <cstdint>
#include <cstring>
#include "gme/gme.h"
#include "gme/Spc_Emu.h"

namespace {
Music_Emu* g_emu = nullptr;
const char* g_last_error = nullptr;
}  // namespace

extern "C" {

// Load a track from a byte buffer already copied into the WASM heap (the JS side does that with
// `_malloc` + `HEAPU8.set`). [track] is 0-based; pass 0 for single-track files.
// Mirrors `loadFile` in Gme.cpp but skips the filesystem hop.
EMSCRIPTEN_KEEPALIVE
int chipbox_gme_load_data(const uint8_t* data, int size, int track) {
    if (g_emu) {
        delete g_emu;
        g_emu = nullptr;
    }
    g_last_error = nullptr;

    // SPC wants its native rate; everything else gets 44.1k. Same policy the JNI wrapper uses.
    // We don't know the type until after gme_open_data resolves it, so optimistically request
    // 44.1k and let the SPC branch re-set if needed. (gme_open_data accepts the rate up-front;
    // we'll re-check by inspecting the emulator type after.)
    g_last_error = gme_open_data(data, size, &g_emu, 44100);
    if (g_last_error) return 1;
    if (!g_emu) {
        g_last_error = "gme_open_data returned null without an error";
        return 1;
    }

    // If we got an SPC emulator, throw away and recreate at SPC's native rate. Cheap because
    // gme_open_data doesn't start_track yet — no decoding work wasted.
    if (g_emu->type() == gme_spc_type) {
        delete g_emu;
        g_emu = nullptr;
        g_last_error = gme_open_data(data, size, &g_emu, Spc_Emu::native_sample_rate);
        if (g_last_error) return 1;
    }

    gme_set_autoload_playback_limit(g_emu, false);
    g_last_error = g_emu->start_track(track);
    return g_last_error ? 1 : 0;
}

// Renders [frames] stereo frames into [target], returns frames actually produced (== frames on
// success, 0 on error — caller checks chipbox_gme_last_error).
EMSCRIPTEN_KEEPALIVE
int chipbox_gme_play(int16_t* target, int frames) {
    if (!g_emu) {
        g_last_error = "no emulator loaded";
        return 0;
    }
    g_last_error = gme_play(g_emu, frames * 2, target);
    return g_last_error ? 0 : frames;
}

EMSCRIPTEN_KEEPALIVE
int chipbox_gme_sample_rate() {
    return g_emu ? g_emu->sample_rate() : 0;
}

EMSCRIPTEN_KEEPALIVE
int chipbox_gme_seek_ms(int position_ms) {
    if (!g_emu) {
        g_last_error = "no emulator loaded";
        return 1;
    }
    g_last_error = g_emu->seek(position_ms);
    return g_last_error ? 1 : 0;
}

EMSCRIPTEN_KEEPALIVE
int chipbox_gme_tell_ms() {
    return g_emu ? g_emu->tell() : 0;
}

// Last error, or empty string. Static lifetime — pointer stays valid until the next libgme call.
EMSCRIPTEN_KEEPALIVE
const char* chipbox_gme_last_error() {
    return g_last_error ? g_last_error : "";
}

EMSCRIPTEN_KEEPALIVE
void chipbox_gme_teardown() {
    if (g_emu) {
        delete g_emu;
        g_emu = nullptr;
    }
    g_last_error = nullptr;
}

}  // extern "C"
