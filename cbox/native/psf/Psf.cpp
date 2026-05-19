#include "Psf.h"

// slopsf -- the PSX (PSF1/PSF2) audio emulation system. Runs the PS1/PS2
// core purely via the HLE IOP-kernel path -- no copyrighted PS2/PS1 BIOS
// blob. PSF1 and PSF2 both boot through the HLE.

static void *pEmu = nullptr;

static bool isPs2Track = false;

static const char *last_error = nullptr;

static void *psf2fs = nullptr;

// psx_init() is global, one-time setup for the whole core.
static bool heInitialized = false;

static bool ensureHeInitialized() {
    if (heInitialized) return true;

    if (psx_init() != 0) {
        last_error = "Failed to initialize PSX core.";
        return false;
    }

    heInitialized = true;
    return true;
}

void loadFile(const char *filename_c_str) {
    teardown();
    last_error = nullptr;
    isPs2Track = false;

    if (!ensureHeInitialized()) return;

    int psf_version = psf_load(filename_c_str, &psf_file_system, 0, 0, 0, 0, 0, 0, 0, 0);

    if (psf_version < 0) {
        last_error = "Not a PSF file";
        return;
    }
    if (psf_version != 1 && psf_version != 2) {
        last_error = "Not a PSF1 or PSF2 file";
        return;
    }

    uint32_t psx_state_size = psx_get_state_size((uint8_t) psf_version);
    pEmu = malloc(psx_state_size);
    if (!pEmu) {
        last_error = "Failed to allocate PSX state.";
        return;
    }

    // Boots the HLE IOP kernel to a known entry point. Must run before any
    // EXE upload; will psx_hang() if psx_init() never succeeded.
    psx_clear_state(pEmu, (uint8_t) psf_version);

    if (psf_version == 1) {
        psf1_load_state state{pEmu, true, 0};

        int ret = psf_load(
                filename_c_str,
                &psf_file_system,
                1,
                psf1_load,
                &state,
                psf1_info,
                &state,
                1,
                0,
                0
        );

        if (ret < 0) {
            last_error = "Invalid PSF1 file";
            free(pEmu);
            pEmu = nullptr;
            return;
        }

        if (state.refresh) psx_set_refresh(pEmu, state.refresh);

        isPs2Track = false;
    } else {
        psf2fs = psf2fs_create();
        if (!psf2fs) {
            last_error = "Failed to allocate PS2 FS.";
            free(pEmu);
            pEmu = nullptr;
            return;
        }

        psf1_load_state state{pEmu, true, 0};

        int ret = psf_load(
                filename_c_str,
                &psf_file_system,
                2,
                psf2fs_load_callback,
                psf2fs,
                psf1_info,
                &state,
                1,
                0,
                0);

        if (ret < 0) {
            last_error = "Invalid PSF2 file";
            psf2fs_delete(psf2fs);
            psf2fs = nullptr;
            free(pEmu);
            pEmu = nullptr;
            return;
        }

        if (state.refresh) psx_set_refresh(pEmu, state.refresh);

        isPs2Track = true;

        // The IOP runtime pulls its modules/data through this callback as it
        // executes; psf2fs holds the decompressed PSF2 filesystem.
        psx_set_readfile(pEmu, (psx_readfile_t) psf2fs_virtual_readfile, psf2fs);
    }
}

int32_t generateBuffer(int16_t *target_array, int32_t frames_per_buffer) {
    if (!pEmu) {
        last_error = "Cannot generate audio: emulator not loaded.";
        return 0;
    }

    uint32_t samples = (uint32_t) frames_per_buffer;

    // Run until the requested number of stereo frames is produced. -1 is a
    // clean PS2 halt (track ended); only <= -2 is a real failure.
    sint32 r = psx_execute(pEmu, 0x7fffffff, target_array, &samples, 0);
    if (r <= -2) {
        last_error = "PSX execution error.";
        return 0;
    }

    return (int32_t) samples;
}

void teardown() {
    if (psf2fs) {
        psf2fs_delete(psf2fs);
        psf2fs = nullptr;
    }
    if (pEmu) {
        free(pEmu);
        pEmu = nullptr;
    }
    isPs2Track = false;
}

const char *get_last_error() {
    return last_error;
}

const char *get_diagnostics() {
    // The HE core has no per-track diagnostics channel.
    return nullptr;
}

int32_t get_sample_rate() {
    // HE clocks the IOP at 33868800 Hz for PS1 and 36864000 Hz for PS2, with a
    // fixed 768 cycles/sample (he/iop.c) — i.e. 44100 Hz for PS1, 48000 Hz for
    // PS2. Reporting 44100 for PS2 makes it play pitched-down.
    return isPs2Track ? 48000 : 44100;
}

static int psf1_info(void *context, const char *name, const char *value) {
    psf1_load_state *state = (psf1_load_state *) context;

    if (!state->refresh && !strcasecmp(name, "_refresh")) {
        state->refresh = atoi(value);
    }

    return 0;
}

int psf1_load(void *context, const uint8_t *exe, size_t exe_size,
              const uint8_t *reserved, size_t reserved_size) {
    psf1_load_state *state = (psf1_load_state *) context;

    if (reserved && reserved_size)
        return -1;

    if (exe_size < 0x800)
        return -1;

    uint32_t addr = get_le32(exe + 0x18);
    uint32_t size = (uint32_t) (exe_size - 0x800);

    addr &= 0x1fffff;
    if (addr < 0x10000 || size > 0x1f0000 || (addr + size) > 0x200000)
        return -1;

    void *iop = psx_get_iop_state(state->emu);
    iop_upload_to_ram(iop, addr, exe + 0x800, size);

    // Region marker lives in the PS-X EXE header; first match wins.
    if (!state->refresh) {
        if (!strncasecmp((const char *) exe + 113, "Japan", 5))
            state->refresh = 60;
        else if (!strncasecmp((const char *) exe + 113, "Europe", 6))
            state->refresh = 50;
        else if (!strncasecmp((const char *) exe + 113, "North America", 13))
            state->refresh = 60;
    }

    if (state->first) {
        void *r3000 = iop_get_r3000_state(iop);
        r3000_setreg(r3000, R3000_REG_PC, get_le32(exe + 0x10));
        r3000_setreg(r3000, R3000_REG_GEN + 29, get_le32(exe + 0x30)); // $sp
        // The PS-X EXE entry contract (per the reference eng_psf.c): the loader
        // sets $gp = gp0 (header 0x14) and $fp = $sp. With no BIOS the
        // HLE does it here; without $gp the driver's gp-relative globals
        // (sequencer/SPU buffers) read garbage -> runs but plays no notes.
        r3000_setreg(r3000, R3000_REG_GEN + 28, get_le32(exe + 0x14)); // $gp
        r3000_setreg(r3000, R3000_REG_GEN + 30, get_le32(exe + 0x30)); // $fp
        state->first = false;
    }

    return 0;
}
