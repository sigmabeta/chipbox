package net.sigmabeta.chipbox.player.common

// Audio-math conversions used throughout the playback stack.
//
// "Frame" = one time slice across all channels (L+R for stereo).
// "Sample" = one channel's value within a frame (a single Short).
// "Byte" = raw storage; one Short = BYTES_PER_SAMPLE bytes.
//
// Sample rate is always Hz (samples-per-second-per-channel = frames-per-second).

fun Double.millisToFrames(sampleRate: Int) = (this * sampleRate.rateInMillis()).toInt()

fun Int.framesToMillis(sampleRate: Int) = this / (sampleRate.rateInMillis())

fun Int.framesToSamples() = (this * SHORTS_PER_FRAME)

fun Int.samplesToFrames() = (this / SHORTS_PER_FRAME)

fun Int.samplesToBytes() = (this * BYTES_PER_SAMPLE)

fun Int.bytesToSamples() = (this / BYTES_PER_SAMPLE)

fun Double.toShortValue() = this.toInt().toShort()

fun Int.rateInMillis() = this / MILLIS_PER_SECOND

fun Double.rateInMillis() = this / MILLIS_PER_SECOND

fun Double.millisToSeconds() = this / MILLIS_PER_SECOND

const val MILLIS_PER_SECOND = 1_000.0
const val CHANNELS_STEREO = 2
const val BYTES_PER_SHORT = 2
const val BYTES_PER_SAMPLE = BYTES_PER_SHORT
const val BYTES_PER_FRAME = BYTES_PER_SAMPLE * CHANNELS_STEREO
const val SHORTS_PER_FRAME = BYTES_PER_FRAME / BYTES_PER_SAMPLE

fun Long.isDivisibleBy(divisor: Int): Boolean = this % divisor == 0L

fun Int.isDivisibleBy(divisor: Int): Boolean = this % divisor == 0

fun ShortArray.clear() {
    forEachIndexed { index, _ -> set(index, 0) }
}
