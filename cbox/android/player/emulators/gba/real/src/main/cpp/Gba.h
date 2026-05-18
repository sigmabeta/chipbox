#ifndef CHIPBOX_GBA_H
#define CHIPBOX_GBA_H

#include <array>
#include <stdio.h>
#include <string.h>

#include <chipbox_psf_io.h>

#include <mgba/core/core.h>
#include <mgba-util/audio-buffer.h>
#include <mgba-util/audio-resampler.h>
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
    int buffer_size_frames;
    bool audio_inited;
    // Upstream mgba dropped blip_buf; we resample the core's native-rate
    // output to a fixed 44100 Hz (what the rest of the pipeline expects)
    // with mAudioResampler, which adapts on the fly if the GSF driver
    // reprograms SOUNDBIAS mid-track.
    struct mAudioBuffer resampled;
    struct mAudioResampler resampler;
};

int gsf_loader(void *, const uint8_t *, size_t, const uint8_t * , size_t );

#endif //CHIPBOX_GBA_H