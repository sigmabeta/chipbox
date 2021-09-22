#ifndef CHIPBOX_TWOSF_H
#define CHIPBOX_TWOSF_H

#include <array>
#include <stdio.h>
#include <string.h>

#include "common/common.h"

#include "Core//vio2sf/vio2sf.h"

void loadFile(const char *);

int32_t generateBuffer(int16_t *, int32_t);

void teardown();

const char *get_last_error();

int32_t get_sample_rate();

#endif //CHIPBOX_TWOSF_H