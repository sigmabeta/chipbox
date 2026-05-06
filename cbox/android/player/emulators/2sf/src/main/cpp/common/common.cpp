#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

void *psf_file_fopen(void *context, const char *uri) {
    try {
        return fopen(uri, "r");
    }
    catch (...) {
        return NULL;
    }
}

size_t psf_file_fread(void *buffer, size_t size, size_t count, void *handle) {
    try {
        auto file = (FILE *) handle;
        return fread(buffer, size, count, file);
    }
    catch (...) {
        return 0;
    }
}

int psf_file_fseek(void *handle, int64_t offset, int whence) {
    try {
        auto file = (FILE *) handle;
        return fseek(file, offset, whence);
    }
    catch (...) {
        return -1;
    }
}

int psf_file_fclose(void *handle) {
    try {
        auto file = (FILE *) handle;
        fclose(file);
        return 0;
    }
    catch (...) {
        return -1;
    }
}

long psf_file_ftell(void *handle) {
    try {
        auto file = (FILE *) handle;
        return ftell(file);
    }
    catch (...) {
        return -1;
    }
}

uint32_t get_le32(void const *p) {
    return (unsigned) ((unsigned char const *) p)[3] << 24 |
           (unsigned) ((unsigned char const *) p)[2] << 16 |
           (unsigned) ((unsigned char const *) p)[1] << 8 |
           (unsigned) ((unsigned char const *) p)[0];
}

void set_le32( void* p, uint32_t n )
{
    ((unsigned char*) p) [0] = (unsigned char) n;
    ((unsigned char*) p) [1] = (unsigned char) (n >> 8);
    ((unsigned char*) p) [2] = (unsigned char) (n >> 16);
    ((unsigned char*) p) [3] = (unsigned char) (n >> 24);
}

#ifdef __cplusplus
};
#endif