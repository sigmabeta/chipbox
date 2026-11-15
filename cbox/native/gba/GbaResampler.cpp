#include "GbaResampler.h"

#include <cmath>

#include <mgba-util/audio-buffer.h>

namespace {
constexpr unsigned kChannels = 2;
}

void GbaResampler::init(struct mAudioResampler *resampler) {
    r = resampler;
    mAudioResamplerInit(r, mINTERPOLATOR_SINC);

    const struct mInterpolatorSinc &sinc = r->sinc;
    const size_t samples = (size_t) sinc.resolution * sinc.width;
    productLut.resize(samples + 1);
    for (size_t i = 0; i <= samples; ++i) {
        // The same multiply upstream does per tap, done once here. It's a plain
        // double product, so the rounding matches.
        productLut[i] = sinc.sincLut[i] * sinc.windowLut[i];
    }
}

void GbaResampler::deinit() {
    if (r) {
        mAudioResamplerDeinit(r);
        r = nullptr;
    }
    productLut.clear();
    scratch.clear();
}

size_t GbaResampler::process() {
    struct mAudioBuffer *source = r->source;
    struct mAudioBuffer *destination = r->destination;
    if (source->channels != kChannels || destination->channels != kChannels) {
        // GBA audio is always stereo; anything else takes the generic path.
        return mAudioResamplerProcess(r);
    }

    const struct mInterpolatorSinc &sinc = r->sinc;
    const int width = (int) sinc.width;
    const unsigned resolution = sinc.resolution;
    const double *sincLut = sinc.sincLut;
    const double *windowLut = sinc.windowLut;
    const double *product = productLut.data();

    const double timestep = r->sourceRate / r->destRate;
    double timestamp = r->timestamp;

    // Copy the source ring into a linear array, with `width` zero frames in
    // front: upstream's _sampleAt returns 0 for a negative index, and the pad
    // gives the same result without a branch on every tap.
    const size_t available = mAudioBufferAvailable(source);
    const size_t pad = (size_t) width;
    scratch.assign((pad + available) * kChannels, 0);
    mAudioBufferDump(source, scratch.data() + pad * kChannels, available, 0);
    const int16_t *frames = scratch.data() + pad * kChannels;

    // Only this function writes `destination` here, so checking free space once
    // up front matches upstream's per-frame "full?" check and failed-write break.
    const size_t room = mAudioBufferCapacity(destination) - mAudioBufferAvailable(destination);

    // Same as upstream mInterpolatorSincInterpolate: `step` depends only on the
    // rate ratio, so it's the same for every frame in this call.
    const unsigned step = timestep < 1 ? resolution * timestep : resolution;
    const bool combined = step == resolution;

    int16_t out[256 * kChannels];
    size_t pending = 0;
    size_t read = 0;

    while (read < room && timestamp + r->highWaterMark < available) {
        const int index = timestamp;
        const double subsample = timestamp - floor(timestamp);
        const unsigned yShift = subsample * step;
        const unsigned xShift = subsample * resolution;

        double sumL = 0.0;
        double sumR = 0.0;
        double kernelSum = 0.0;
        double kernel;

        for (int i = 1 - width; i <= width; ++i) {
            const unsigned magnitude = i >= 0 ? i : -i;
            unsigned window = magnitude * resolution;
            window = yShift > window ? yShift - window : window - yShift;

            if (combined) {
                // step == resolution makes sinc == window, so one table read
                // replaces two.
                kernel = product[window];
            } else {
                unsigned sincIndex = magnitude * step;
                sincIndex = xShift > sincIndex ? xShift - sincIndex : sincIndex - xShift;
                kernel = sincLut[sincIndex] * windowLut[window];
            }
            kernelSum += kernel;

            const int16_t *frame = frames + (ptrdiff_t) (index + i) * kChannels;
            sumL += frame[0] * kernel;
            sumR += frame[1] * kernel;
        }

        out[pending * kChannels] = (int16_t) (sumL / kernelSum);
        out[pending * kChannels + 1] = (int16_t) (sumR / kernelSum);
        if (++pending == sizeof(out) / sizeof(out[0]) / kChannels) {
            mAudioBufferWrite(destination, out, pending);
            pending = 0;
        }

        timestamp += timestep;
        ++read;
    }
    if (pending) {
        mAudioBufferWrite(destination, out, pending);
    }

    if (r->consume && timestamp > r->lowWaterMark) {
        size_t drop = timestamp - r->lowWaterMark;
        drop = mAudioBufferRead(source, nullptr, drop);
        timestamp -= drop;
    }
    r->timestamp = timestamp;
    return read;
}
