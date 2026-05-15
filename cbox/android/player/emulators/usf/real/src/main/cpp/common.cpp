#include "common.h"

#include <cstdio>

void *psf_file_fopen(void *context, const char *uri) {
    return fopen(uri, "rb");
}

size_t psf_file_fread(void *buffer, size_t size, size_t count, void *handle) {
    return fread(buffer, size, count, (FILE *) handle);
}

int psf_file_fseek(void *handle, int64_t offset, int whence) {
    return fseek((FILE *) handle, offset, whence);
}

int psf_file_fclose(void *handle) {
    fclose((FILE *) handle);
    return 0;
}

long psf_file_ftell(void *handle) {
    return ftell((FILE *) handle);
}
