#ifndef CHIPBOX_VGM_H
#define CHIPBOX_VGM_H

#include <stdint.h>

void loadFile(const char *path);

int32_t generateBuffer(int16_t *target, int32_t frames);

void teardown();

const char *get_last_error();

int32_t get_sample_rate();

#endif //CHIPBOX_VGM_H
