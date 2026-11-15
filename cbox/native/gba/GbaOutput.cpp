#include "GbaOutput.h"

#include <algorithm>
#include <cstring>

namespace {
// Resampler source window, in frames. One GBA frame at 262144 Hz is ~4400.
constexpr size_t kSourceFrames = 0x4000;
// The output rate is never more than 2x the source rate (32768 -> 65536), so
// one pass over a full source window fits in here.
constexpr size_t kResampledFrames = kSourceFrames * 2 + 64;
// Compact the queue once this many read frames sit in front of it.
constexpr size_t kCompactFrames = 0x8000;
}

GbaOutput::~GbaOutput() {
    stop();
}

void GbaOutput::start(int32_t outputRate) {
    stop();
    outRate = outputRate;
}

void GbaOutput::stop() {
    endResampling();
    if (buffersInited) {
        mAudioBufferDeinit(&source);
        mAudioBufferDeinit(&resampled);
        buffersInited = false;
    }
    queue.clear();
    head = 0;
    outRate = 0;
}

void GbaOutput::feed(const int16_t *frames, size_t count, int32_t rate) {
    if (count == 0) {
        return;
    }

    if (rate == outRate) {
        // Whatever the resampler still holds is the last few source frames of
        // the old rate (its look-ahead). Dropping them at a rate switch costs
        // well under a millisecond, and the switch itself is a discontinuity.
        endResampling();
        queue.insert(queue.end(), frames, frames + count * kChannels);
        return;
    }

    if (resampleRate != rate) {
        beginResampling(rate);
    }

    size_t done = 0;
    while (done < count) {
        const size_t room = mAudioBufferCapacity(&source) - mAudioBufferAvailable(&source);
        const size_t n = std::min(room, count - done);
        mAudioBufferWrite(&source, frames + done * kChannels, n);
        done += n;
        drainResampled();
    }
}

size_t GbaOutput::read(int16_t *target, size_t count) {
    const size_t n = std::min(count, queued());
    memcpy(target, queue.data() + head * kChannels, n * kChannels * sizeof(int16_t));
    head += n;
    if (head == queue.size() / kChannels) {
        queue.clear();
        head = 0;
    } else if (head >= kCompactFrames) {
        queue.erase(queue.begin(), queue.begin() + (ptrdiff_t) (head * kChannels));
        head = 0;
    }
    return n;
}

void GbaOutput::beginResampling(int32_t rate) {
    endResampling();
    if (!buffersInited) {
        mAudioBufferInit(&source, kSourceFrames, kChannels);
        mAudioBufferInit(&resampled, kResampledFrames, kChannels);
        buffersInited = true;
    }
    mAudioBufferClear(&source);
    mAudioBufferClear(&resampled);
    resampler.init(&state);
    mAudioResamplerSetSource(&state, &source, rate, true);
    mAudioResamplerSetDestination(&state, &resampled, outRate);
    resampleRate = rate;
}

void GbaOutput::endResampling() {
    if (resampleRate != 0) {
        resampler.deinit();
        resampleRate = 0;
    }
}

void GbaOutput::drainResampled() {
    int16_t chunk[1024 * kChannels];
    while (true) {
        const size_t produced = resampler.process();
        size_t n;
        while ((n = mAudioBufferRead(&resampled, chunk, sizeof(chunk) / sizeof(chunk[0]) / kChannels)) > 0) {
            queue.insert(queue.end(), chunk, chunk + n * kChannels);
        }
        if (produced == 0) {
            return;
        }
    }
}
