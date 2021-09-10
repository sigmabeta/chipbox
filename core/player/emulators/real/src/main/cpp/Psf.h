#ifndef CHIPBOX_PSF_H
#define CHIPBOX_PSF_H

#include <array>
#include <stdio.h>
#include <string.h>

#include "aopsf/psflib.h"
#include "aopsf/psf2fs.h"
#include "aopsf/psx_external.h"

void loadFile(const char *);

int32_t generateBuffer(int16_t *, int32_t);

void teardown();

const char *get_last_error();

int32_t get_sample_rate();

static void *psf_file_fopen(void *context, const char *uri);

static size_t psf_file_fread(void *buffer, size_t size, size_t count, void *handle);

static int psf_file_fseek(void *handle, int64_t offset, int whence);

static int psf_file_fclose(void *handle);

static long psf_file_ftell(void *handle);

int psf1_load(
        void *context,
        const uint8_t *exe,
        size_t exe_size,
        const uint8_t *reserved,
        size_t reserved_size
);

static int psf1_info(void *context, const char *name, const char *value);

const psf_file_callbacks psf_file_system =
        {
                "\\/|:",
                NULL,
                psf_file_fopen,
                psf_file_fread,
                psf_file_fseek,
                psf_file_fclose,
                psf_file_ftell
        };

struct psf1_load_state {
    void *emu;
    bool first;
    unsigned refresh;
};


#endif //CHIPBOX_PSF_H