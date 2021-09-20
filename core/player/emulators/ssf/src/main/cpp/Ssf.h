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

struct sdsf_loader_state
{
    void *emu;
    void *yam;
    size_t version;
    uint8_t * data;
    size_t data_size;
};
#endif //CHIPBOX_SSF_H