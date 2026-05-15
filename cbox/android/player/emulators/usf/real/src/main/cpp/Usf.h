#ifndef CHIPBOX_USF_H
#define CHIPBOX_USF_H

#include <stdint.h>

void loadFile(const char *path);

int32_t generateBuffer(int16_t *target, int32_t frames);

void teardown();

const char *get_last_error();

int32_t get_sample_rate();

#endif //CHIPBOX_USF_H
