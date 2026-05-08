#ifndef CHIPBOX_COMMON_H
#define CHIPBOX_COMMON_H

#include "../psflib/psflib.h"
#include "../psflib/psf2fs.h"

void *psf_file_fopen(void *context, const char *uri);

size_t psf_file_fread(void *buffer, size_t size, size_t count, void *handle);

int psf_file_fseek(void *handle, int64_t offset, int whence);

int psf_file_fclose(void *handle);

long psf_file_ftell(void *handle);

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

#endif //CHIPBOX_COMMON_H
