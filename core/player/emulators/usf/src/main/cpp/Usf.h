#ifndef CHIPBOX_USF_H
#define CHIPBOX_USF_H

#include <array>
#include <stdio.h>
#include <string.h>

#include "common/common.h"

#include "lazyusf2/usf/usf.h"

void loadFile(const char *);

int32_t generateBuffer(int16_t *, int32_t);

void teardown();

const char *get_last_error();

int32_t get_sample_rate();

int usf_loader(
        void *,
        const uint8_t *,
        size_t ,
        const uint8_t *,
        size_t
);

int usf_info(
        void *,
        const char *,
        const char *
);

struct usf_loader_state
{
    uint32_t enable_compare;
    uint32_t enable_fifo_full;
    void * emu_state;

    usf_loader_state()
            : enable_compare(0), enable_fifo_full(0),
              emu_state(0)
    { }

    ~usf_loader_state()
    {
        if ( emu_state )
            free( emu_state );
    }
};

#endif //CHIPBOX_USF_H