#ifndef CHIPBOX_GBA_H
#define CHIPBOX_GBA_H

#include <array>
#include <stdio.h>
#include <string.h>

#include "common/common.h"

#include <mgba/core/core.h>
#include <mgba/core/blip_buf.h>
#include <mgba-util/vfs.h>

void loadFile(const char *);

int32_t generateBuffer(int16_t *, int32_t);

void teardown();

const char *get_last_error();

int32_t get_sample_rate();

struct gsf_loader_state
{
    int entry_set;
    uint32_t entry;
    uint8_t * data;
    size_t data_size;
    gsf_loader_state() : entry_set( 0 ), data( 0 ), data_size( 0 ) { }
    ~gsf_loader_state() { if ( data ) free( data ); }
};

struct gsf_running_state
{
    struct mAVStream stream;
    int samples_available;
    int buffer_size_samples;
    int16_t * samples;
};

int gsf_loader(void *, const uint8_t *, size_t, const uint8_t * , size_t );

static void _gsf_postAudioBuffer(struct mAVStream *, blip_t *, blip_t *);

#endif //CHIPBOX_GBA_H