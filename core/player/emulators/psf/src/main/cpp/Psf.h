#ifndef CHIPBOX_PSF_H
#define CHIPBOX_PSF_H

#include <array>
#include <stdio.h>
#include <string.h>

#include "common/common.h"
#include "aopsf/psx_external.h"

void loadFile(const char *);

int32_t generateBuffer(int16_t *, int32_t);

void teardown();

const char *get_last_error();

int32_t get_sample_rate();

int psf1_load(
        void *context,
        const uint8_t *exe,
        size_t exe_size,
        const uint8_t *reserved,
        size_t reserved_size
);

static int psf1_info(void *context, const char *name, const char *value);

struct psf1_load_state {
    void *emu;
    bool first;
    unsigned refresh;
};


#endif //CHIPBOX_PSF_H