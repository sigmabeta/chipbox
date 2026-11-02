#ifndef CHIPBOX_ENV_H
#define CHIPBOX_ENV_H

// Cached access to debug-log environment switches, shared by every Chipbox
// emulator core (adopted first by the PSF core, cbox/native/psf/slop/).
//
// The cores carry years of development-time logging gated on an environment
// variable (`HLE_DMA=1`, `SPU2LOG=1`, ...). Testing `getenv` at the gate is a
// linear scan of `environ`, and the gate often sits on a hot path: the PSF2
// per-sample pump and the per-register HLE hooks reach these sites thousands
// of times per second of audio, which cost several percent of render time on a
// desktop-sized environment. Resolving each switch once and remembering the
// answer reduces the gate to a load and a compare.
//
// Usage -- give the call site a file-scope cache slot, then ask for the flag:
//
//     static int env_hle_dma = CHIPBOX_ENV_UNREAD;
//     ...
//     if (chipbox_env_flag(&env_hle_dma, "HLE_DMA")) { ... }
//
// Semantics: the switch is ON when the variable is present and its first
// character is not '0' (so "1", "yes", and even "" are ON), and OFF when the
// variable is unset or starts with '0'.
//
// That is the contract the ad-hoc `(e && *e != '0')` checks already had. It is
// narrower than the bare `if (getenv(...))` gates some call sites were migrated
// from, where "0" counted as ON: there `X=0` now means OFF. Unset still means
// OFF and any other value still means ON, so no documented switch changes
// (`HLE_DMA=1`, `HLE_TRACE=1`, ...).
//
// The value is latched on first use, so a switch must be in the environment
// before the first call for it to take effect -- which is how debug runs are
// started (`HLE_DMA=1 ./gradlew :testing:abrender:run ...`), and what makes the
// hot path free. Nothing in the cores changes a switch mid-run.
//
// The cache slot is caller-owned and plain memory, so this needs no
// initialisation, no teardown and no CMake wiring: it is header-only and works
// unchanged on Android (C11), the JVM host and Emscripten. A slot is per
// translation unit, so a switch consulted from several files costs one
// `getenv` each on first use -- a handful per process, never per sample.

#include <stdlib.h>

// A cache slot that has not consulted the environment yet.
#define CHIPBOX_ENV_UNREAD (-1)

// Uncached read of a boolean switch, with the "set and not '0'" semantics.
static inline int chipbox_env_read_flag(const char *name) {
    const char *value = getenv(name);
    return (value && *value != '0') ? 1 : 0;
}

// Cached read of a boolean switch. `cache` must start at CHIPBOX_ENV_UNREAD.
static inline int chipbox_env_flag(int *cache, const char *name) {
    int enabled = *cache;
    if (enabled == CHIPBOX_ENV_UNREAD) {
        enabled = chipbox_env_read_flag(name);
        *cache = enabled;
    }
    return enabled;
}

#endif // CHIPBOX_ENV_H
