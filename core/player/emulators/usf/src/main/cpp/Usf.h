#ifndef CHIPBOX_USF_H
#define CHIPBOX_USF_H

#include <array>
#include <stdio.h>
#include <string.h>

#include "common/common.h"

void loadFile(const char *);

int32_t generateBuffer(int16_t *, int32_t);

void teardown();

const char *get_last_error();

int32_t get_sample_rate();

#endif //CHIPBOX_USF_H