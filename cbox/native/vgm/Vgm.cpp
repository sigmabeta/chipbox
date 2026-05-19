#include "Vgm.h"

#include "player/playera.hpp"
#include "player/vgmplayer.hpp"
#include "utils/FileLoader.h"
#include "utils/DataLoader.h"

static const uint32_t kSampleRate = 44100;
// Internal mix buffer size in samples (frames). Must be >= the largest single
// Render call we will ever issue. Render() clamps to this internally.
static const uint32_t kInternalBufferFrames = 4096;

static PlayerA *g_player = nullptr;
static DATA_LOADER *g_loader = nullptr;
static const char *g_last_error = nullptr;

static void teardown_internal() {
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
}

void loadFile(const char *path) {
    teardown_internal();
    g_last_error = nullptr;

    g_loader = FileLoader_Init(path);
    if (g_loader == nullptr) {
        g_last_error = "FileLoader_Init failed";
        return;
    }

    if (DataLoader_Load(g_loader)) {
        g_last_error = "DataLoader_Load failed";
        DataLoader_Deinit(g_loader);
        g_loader = nullptr;
        return;
    }

    g_player = new PlayerA();
    g_player->RegisterPlayerEngine(new VGMPlayer());

    // Chipbox's Emulator base class owns track-length tracking and triggers
    // end-of-track itself; libvgm should render indefinitely. Disable the
    // per-loop fade-out and silence trim that PlayerA applies by default
    // (loopCount=2, which calls FadeOut() and ultimately sets PLAYSTATE_FIN,
    // making Render() short-circuit — playera.cpp:580, 633).
    PlayerA::Config cfg = g_player->GetConfiguration();
    cfg.loopCount = 0;
    cfg.fadeSmpls = 0;
    cfg.endSilenceSmpls = 0;
    g_player->SetConfiguration(cfg);

    if (g_player->SetOutputSettings(kSampleRate, 2, 16, kInternalBufferFrames)) {
        g_last_error = "SetOutputSettings failed";
        teardown_internal();
        return;
    }

    if (g_player->LoadFile(g_loader)) {
        g_last_error = "LoadFile failed";
        teardown_internal();
        return;
    }

    if (g_player->Start()) {
        g_last_error = "Start failed";
        teardown_internal();
        return;
    }
}

int32_t generateBuffer(int16_t *target, int32_t frames) {
    if (g_player == nullptr) {
        return 0;
    }
    // PlayerA::Render takes a byte count and returns bytes written.
    // 16-bit stereo => 4 bytes per frame.
    uint32_t bytesWritten = g_player->Render(frames * 4u, target);
    return static_cast<int32_t>(bytesWritten / 4u);
}

void teardown() {
    teardown_internal();
}

const char *get_last_error() {
    return g_last_error;
}

int32_t get_sample_rate() {
    return static_cast<int32_t>(kSampleRate);
}
