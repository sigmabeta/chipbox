#ifndef CHIPBOX_GBA_OUTPUT_H
#define CHIPBOX_GBA_OUTPUT_H

#include <cstddef>
#include <cstdint>
#include <vector>

#include <mgba-util/audio-buffer.h>
#include <mgba-util/audio-resampler.h>

#include "GbaResampler.h"

// The GSF output stage: delivers the core's audio at one fixed output rate per
// track, resampling only what the core produced at some other rate.
//
// A GSF driver picks its mixing rate by writing SOUNDBIAS. mGBA starts every
// track at 32768 Hz, and the driver moves to its real rate (usually 65536 Hz)
// during init: on a 100-game survey the last change came 0.27 s in, and none
// came after that. Gba.cpp pre-rolls the start of the track, starts this stage
// at the settled rate, and feeds it every frame's samples with the rate they
// were made at. Samples at the output rate pass through untouched. Anything
// else (the pre-switch start, a later rate change, a rate above the cap)
// goes through GbaResampler.
//
// Resampling everything to 44100 Hz cost ~70% of render time on device, and is
// what held the fastest phones at the same speed.
class GbaOutput {
public:
    GbaOutput() = default;
    GbaOutput(const GbaOutput &) = delete;
    GbaOutput &operator=(const GbaOutput &) = delete;
    ~GbaOutput();

    // Discards anything queued and fixes the output rate.
    void start(int32_t outputRate);

    // Drops queued audio and frees the resampler.
    void stop();

    // Appends `count` interleaved stereo frames that the core produced at
    // `rate` Hz.
    void feed(const int16_t *frames, size_t count, int32_t rate);

    // Frames ready to read, at outputRate().
    size_t queued() const { return queue.size() / kChannels - head; }

    // Copies up to `count` frames into `target`; returns how many it copied.
    size_t read(int16_t *target, size_t count);

    int32_t outputRate() const { return outRate; }

    static constexpr unsigned kChannels = 2;

private:
    void beginResampling(int32_t rate);
    void endResampling();
    void drainResampled();

    int32_t outRate = 0;

    // Source rate the resampler is set up for; 0 while passing through.
    int32_t resampleRate = 0;
    bool buffersInited = false;
    struct mAudioBuffer source = {};
    struct mAudioBuffer resampled = {};
    struct mAudioResampler state = {};
    GbaResampler resampler;

    // Interleaved output frames; frames before `head` have been read.
    std::vector<int16_t> queue;
    size_t head = 0;
};

#endif //CHIPBOX_GBA_OUTPUT_H
