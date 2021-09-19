#ifndef CHIPBOX_SSF_H
#define CHIPBOX_SSF_H

#include <array>
#include <stdio.h>
#include <string.h>

#include "common/common.h"

#include "Core/sega.h"
#include "Core/dcsound.h"
#include "Core/satsound.h"
#include "Core/yam.h"

void loadFile(const char *);

int32_t generateBuffer(int16_t *, int32_t);

void teardown();

const char *get_last_error();

int32_t get_sample_rate();

static int sdsf_load(
        void *context,
        const uint8_t *exe, size_t exe_size,
        const uint8_t *reserved,
        size_t reserved_size
);

struct sdsf_load_state {
    uint8_t *state = 0;
    uint32_t state_size = 0;
};


#endif //CHIPBOX_SSF_H