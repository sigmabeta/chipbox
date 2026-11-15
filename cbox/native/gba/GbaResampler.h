#ifndef CHIPBOX_GBA_RESAMPLER_H
#define CHIPBOX_GBA_RESAMPLER_H

#include <cstddef>
#include <cstdint>
#include <vector>

#include <mgba-util/audio-resampler.h>

// A faster drop-in for mAudioResamplerProcess() on a stereo mINTERPOLATOR_SINC
// resampler, producing bit-identical output.
//
// Upstream's sinc path was ~70% of GSF render time and is why GSF playback speed
// barely changed from one phone to the next. Per output frame, and separately
// for each channel, it makes 16 taps. Each tap reads two 512 KB double LUTs
// (sinc + window) at a 64 KB stride, which almost always misses L1/L2, and
// fetches its sample through a function pointer -> mAudioBufferPeek ->
// mCircleBufferDump (a 2-byte memcpy). Cache-miss latency is roughly the same
// on every phone, so it set the speed limit.
//
// This version computes the 16 kernel weights once per output frame and shares
// them across both channels. When downsampling (source rate >= 44100 Hz, the
// usual GSF case), the sinc and window indices are always equal, so it reads one
// precombined sinc*window LUT. It copies the source window into a linear array
// once per call. The kernel, the sums and their order are the same as upstream,
// so the output matches bit for bit (scripts/abrender/verify.sh checks this).
struct GbaResampler {
    // Initialises `resampler` exactly like mAudioResamplerInit(.., SINC) and
    // builds the combined LUT from its tables.
    void init(struct mAudioResampler *resampler);
    void deinit();

    // Same contract and side effects as mAudioResamplerProcess(resampler).
    size_t process();

private:
    struct mAudioResampler *r = nullptr;
    std::vector<double> productLut;  // sincLut[i] * windowLut[i]
    std::vector<int16_t> scratch;    // kPad zero frames + linearised source, interleaved
};

#endif //CHIPBOX_GBA_RESAMPLER_H
