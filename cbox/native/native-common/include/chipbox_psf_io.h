#ifndef CHIPBOX_PSF_IO_H
#define CHIPBOX_PSF_IO_H

#include <psflib.h>
#include <psf2fs.h>

#ifdef __cplusplus
extern "C" {
#endif

void *psf_file_fopen(void *context, const char *uri);

size_t psf_file_fread(void *buffer, size_t size, size_t count, void *handle);

int psf_file_fseek(void *handle, int64_t offset, int whence);

int psf_file_fclose(void *handle);

long psf_file_ftell(void *handle);

extern const psf_file_callbacks psf_file_system;

uint32_t get_le32(void const *p);

void set_le32(void *p, uint32_t n);

#ifdef __cplusplus
}
#endif

#endif //CHIPBOX_PSF_IO_H
