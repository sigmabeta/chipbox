#ifndef CHIPBOX_PSF_H
#define CHIPBOX_PSF_H

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <strings.h>

#include <chipbox_psf_io.h>

#include "slop/psx.h"
#include "slop/iop.h"
#include "slop/r3000.h"

void loadFile(const char *);

int32_t generateBuffer(int16_t *, int32_t);

void teardown();

const char *get_last_error();

const char *get_diagnostics();

int32_t get_sample_rate();

// PSF1 spec: the initial PC/SP/region come from the FIRST PS-X EXE in load
// order (the deepest _lib, i.e. the sound driver), not the main psf. psflib
// delivers the deepest lib first, so `first` captures exactly that one.
struct psf1_load_state {
    void *emu;
    bool first;
    unsigned refresh;
};

int psf1_load(
        void *context,
        const uint8_t *exe,
        size_t exe_size,
        const uint8_t *reserved,
        size_t reserved_size
);

static int psf1_info(void *context, const char *name, const char *value);

#endif //CHIPBOX_PSF_H
