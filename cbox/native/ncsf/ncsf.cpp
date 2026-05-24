#include "ncsf.h"

#include <chipbox_psf_io.h>

#include "Core/sseqplayer/Player.h"
#include "Core/sseqplayer/SDAT.h"

#include <cstring>
#include <exception>
#include <memory>
#include <string>
#include <vector>

namespace {

// NCSF is a PSF-family format (header "PSF" + version byte 0x25) targeting the Nintendo DS.
// Unlike 2SF, it is not ARM emulation: the PSF "program" section is a Nitro Composer SDAT
// archive (sequences + instrument banks + wave samples) and the PSF "reserved" section holds
// the index of the SSEQ to play. We hand the assembled SDAT to the SSEQ Player, a software
// emulation of the DS's 16 sound channels (by Naram Qashat / CyberBotX, after fincs' FeOS
// Sound System), and pull stereo PCM from it.
const uint32_t NCSF_VERSION = 0x25;
const int32_t SAMPLE_RATE = 44100;
const uint32_t BYTES_PER_STEREO_FRAME = 4; // 2 channels * 16-bit

std::vector<uint8_t> g_rom;
std::unique_ptr<SDAT> g_sdat;
std::unique_ptr<Player> g_player;
std::vector<uint8_t> g_sample_buffer;
std::string g_error_storage;
const char *g_last_error = nullptr;

// Accumulated across the _lib chain by psf_load's callback.
struct LoaderState {
    std::vector<uint8_t> rom; // the assembled SDAT
    uint32_t sseq = 0;        // index of the sequence to play
};

// psf_load delivers each chain member's decompressed exe + reserved bodies, deepest _lib first.
// For NCSF the exe body is the SDAT (carried by the .ncsflib) and the reserved body is the
// little-endian SSEQ index (carried by the .minincsf). Overlay exe at offset 0 so the
// highest-priority file wins, matching every other PSF assembler.
int ncsf_loader(void *context, const uint8_t *exe, size_t exe_size,
                const uint8_t *reserved, size_t reserved_size) {
    auto *state = static_cast<LoaderState *>(context);
    if (exe && exe_size) {
        if (state->rom.size() < exe_size) {
            state->rom.resize(exe_size);
        }
        memcpy(state->rom.data(), exe, exe_size);
    }
    if (reserved && reserved_size >= sizeof(uint32_t)) {
        state->sseq = get_le32(reserved);
    }
    return 0;
}

// Smallest power of two >= n. The SSEQ Player reads the SDAT through PseudoFile's unchecked
// operator[]; padding the backing buffer up to a power of two (zero-filled) gives slack against
// reads that run slightly past a section's declared end.
size_t round_up_pow2(size_t n) {
    size_t p = 1;
    while (p && p < n) {
        p <<= 1;
    }
    return p ? p : n;
}

void teardown_internal() {
    if (g_player) {
        g_player->Stop(true); // true = kill sound output
        g_player.reset();
    }
    g_sdat.reset();
    g_rom.clear();
    g_rom.shrink_to_fit();
}

} // namespace

void loadFile(const char *path) {
    teardown_internal();
    g_last_error = nullptr;

    LoaderState loader;
    if (psf_load(path, &psf_file_system, NCSF_VERSION,
                 ncsf_loader, &loader,
                 nullptr, nullptr, 0, nullptr, nullptr) < 0) {
        g_last_error = "Not a valid NCSF file.";
        return;
    }

    if (loader.rom.empty()) {
        g_last_error = "NCSF contains no SDAT data.";
        return;
    }

    g_rom = std::move(loader.rom);
    g_rom.resize(round_up_pow2(g_rom.size()), 0);

    try {
        PseudoFile file;
        file.data = &g_rom;
        g_sdat = std::make_unique<SDAT>(file, loader.sseq);

        g_player = std::make_unique<Player>();
        g_player->sampleRate = SAMPLE_RATE;
        g_player->interpolation = INTERPOLATION_SINC;
        if (!g_player->Setup(g_sdat->sseq.get())) {
            g_last_error = "Failed to set up NCSF player.";
            teardown_internal();
            return;
        }
        g_player->Timer(); // prime the emulation
    } catch (const std::exception &e) {
        g_error_storage = std::string("Failed to parse SDAT: ") + e.what();
        g_last_error = g_error_storage.c_str();
        teardown_internal();
    }
}

int32_t generateBuffer(int16_t *target_array, int32_t output_size_frames) {
    if (!g_player) {
        g_last_error = "Cannot generate audio: emulator not loaded.";
        return 0;
    }

    const size_t bytes = static_cast<size_t>(output_size_frames) * BYTES_PER_STEREO_FRAME;
    g_sample_buffer.resize(bytes);
    g_player->GenerateSamples(g_sample_buffer, 0, output_size_frames);
    memcpy(target_array, g_sample_buffer.data(), bytes);

    return output_size_frames;
}

void teardown() {
    teardown_internal();
}

const char *get_last_error() {
    return g_last_error;
}

int32_t get_sample_rate() {
    return SAMPLE_RATE;
}
