#ifdef __cplusplus
extern "C" {
#endif

#include "common.h"

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

double now_ms_dbl() {
    struct timespec res;
    clock_gettime(CLOCK_REALTIME, &res);
    return 1000.0 * res.tv_sec + (double) res.tv_nsec / 1e6;
}

uint32_t now_ms_int() {
    struct timespec res;
    clock_gettime(CLOCK_REALTIME, &res);
    return 1000L * res.tv_sec + res.tv_nsec / 1e6;
}
#ifdef __cplusplus
}
#endif