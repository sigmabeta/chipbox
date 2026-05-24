#ifndef CHIPBOX_NCSF_H
#define CHIPBOX_NCSF_H

#include <cstdint>

void loadFile(const char *);

int32_t generateBuffer(int16_t *, int32_t);

void teardown();

const char *get_last_error();

int32_t get_sample_rate();

#endif //CHIPBOX_NCSF_H
