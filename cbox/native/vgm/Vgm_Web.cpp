// WASM-target wrapper for libvgm. The host JNI build loads tracks via `FileLoader_Init(path)`
// which calls `fopen` — the browser has no filesystem, so this translation unit instead takes
// raw bytes from the Emscripten heap and uses `MemoryLoader_Init(buffer, length)`. Everything
// else (PlayerA configuration, Render loop, teardown) is identical to `Vgm.cpp`.
//
// All entry points are flat C and `EMSCRIPTEN_KEEPALIVE` so emcc doesn't dead-strip them in
// `-Oz`. The Kotlin/JS bindings reach the exports via the Emscripten module's `_<name>`.

#include <emscripten.h>
#include <cstdint>
#include <cstring>

#include "player/playera.hpp"
#include "player/vgmplayer.hpp"
#include "utils/MemoryLoader.h"
#include "utils/DataLoader.h"

namespace {
constexpr uint32_t kSampleRate = 44100;
// Internal mix buffer size in samples (frames). PlayerA::Render clamps to this internally.
constexpr uint32_t kInternalBufferFrames = 4096;

PlayerA* g_player = nullptr;
DATA_LOADER* g_loader = nullptr;
// MemoryLoader_Init holds onto the buffer pointer — keep our own copy alive for the loader's
// lifetime so the bytes outlive the JS-side malloc/free pair.
uint8_t* g_buffer = nullptr;
const char* g_last_error = nullptr;

void teardown_internal() {
    if (g_player != nullptr) {
        g_player->Stop();
        g_player->UnloadFile();
        g_player->UnregisterAllPlayers();
        delete g_player;
        g_player = nullptr;
    }
    if (g_loader != nullptr) {
        DataLoader_Deinit(g_loader);
        g_loader = nullptr;
    }
    if (g_buffer != nullptr) {
        delete[] g_buffer;
        g_buffer = nullptr;
    }
}
}  // namespace

extern "C" {

EMSCRIPTEN_KEEPALIVE
int chipbox_vgm_load_data(const uint8_t* data, int size) {
    teardown_internal();
    g_last_error = nullptr;

    g_buffer = new (std::nothrow) uint8_t[size];
    if (g_buffer == nullptr) {
        g_last_error = "out of memory copying track bytes";
        return 1;
    }
    std::memcpy(g_buffer, data, static_cast<size_t>(size));

    g_loader = MemoryLoader_Init(g_buffer, static_cast<uint32_t>(size));
    if (g_loader == nullptr) {
        g_last_error = "MemoryLoader_Init failed";
        teardown_internal();
        return 1;
    }

    if (DataLoader_Load(g_loader)) {
        g_last_error = "DataLoader_Load failed";
        teardown_internal();
        return 1;
    }

    g_player = new PlayerA();
    g_player->RegisterPlayerEngine(new VGMPlayer());

    // Chipbox's Emulator base class owns track-length tracking and triggers end-of-track itself;
    // libvgm should render indefinitely. Disable PlayerA's default loop fade-out and silence
    // trim so it doesn't go PLAYSTATE_FIN out from under us.
    PlayerA::Config cfg = g_player->GetConfiguration();
    cfg.loopCount = 0;
    cfg.fadeSmpls = 0;
    cfg.endSilenceSmpls = 0;
    g_player->SetConfiguration(cfg);

    if (g_player->SetOutputSettings(kSampleRate, 2, 16, kInternalBufferFrames)) {
        g_last_error = "SetOutputSettings failed";
        teardown_internal();
        return 1;
    }
    if (g_player->LoadFile(g_loader)) {
        g_last_error = "LoadFile failed";
        teardown_internal();
        return 1;
    }
    if (g_player->Start()) {
        g_last_error = "Start failed";
        teardown_internal();
        return 1;
    }
    return 0;
}

EMSCRIPTEN_KEEPALIVE
int chipbox_vgm_play(int16_t* target, int frames) {
    if (g_player == nullptr) {
        g_last_error = "no track loaded";
        return 0;
    }
    // PlayerA::Render takes a byte count and returns bytes written. 16-bit stereo = 4 B/frame.
    uint32_t bytesWritten = g_player->Render(static_cast<uint32_t>(frames) * 4u, target);
    return static_cast<int>(bytesWritten / 4u);
}

EMSCRIPTEN_KEEPALIVE
int chipbox_vgm_sample_rate() { return static_cast<int>(kSampleRate); }

EMSCRIPTEN_KEEPALIVE
const char* chipbox_vgm_last_error() { return g_last_error ? g_last_error : ""; }

EMSCRIPTEN_KEEPALIVE
void chipbox_vgm_teardown() {
    teardown_internal();
    g_last_error = nullptr;
}

}  // extern "C"
