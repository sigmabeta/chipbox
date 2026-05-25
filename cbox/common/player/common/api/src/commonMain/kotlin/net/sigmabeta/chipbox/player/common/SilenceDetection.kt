package net.sigmabeta.chipbox.player.common

import kotlin.math.abs

/**
 * Magnitude (16-bit signed PCM) below which a sample is treated as silent. Matches GME's
 * `silence_threshold` convention — ~ -94 dBFS, well below audible perception, and tolerant of
 * emulator quirks like a constant DC offset of -1 (`0xFFFF`) that some PSF rips end with.
 */
const val SILENCE_THRESHOLD_AMPLITUDE: Int = 16

/**
 * Returns true if the first [frameCount] stereo frames of [buffer] are all within
 * [SILENCE_THRESHOLD_AMPLITUDE] of zero. `frameCount <= 0` returns false (no audio to judge).
 */
fun isBufferSilent(buffer: ShortArray, frameCount: Int): Boolean {
    if (frameCount <= 0) return false
    val sampleCount = frameCount * SHORTS_PER_FRAME
    for (i in 0 until sampleCount) {
        if (abs(buffer[i].toInt()) > SILENCE_THRESHOLD_AMPLITUDE) return false
    }
    return true
}

/**
 * Index of the first frame (within the first [frameCount] frames of [buffer]) that contains a
 * sample louder than [SILENCE_THRESHOLD_AMPLITUDE], or `-1` if those frames are all silent /
 * `frameCount <= 0`. Used to drop the run of silence many tracks open with.
 */
fun firstAudibleFrame(buffer: ShortArray, frameCount: Int): Int {
    val sampleCount = (frameCount * SHORTS_PER_FRAME).coerceAtLeast(0)
    for (i in 0 until sampleCount) {
        if (abs(buffer[i].toInt()) > SILENCE_THRESHOLD_AMPLITUDE) {
            return i / SHORTS_PER_FRAME
        }
    }
    return -1
}

/**
 * Largest absolute sample magnitude across the first [frameCount] frames of [buffer] (`0` when
 * there are none). Returned as an `Int` so the `abs` of [Short.MIN_VALUE] doesn't overflow.
 */
fun maxAmplitude(buffer: ShortArray, frameCount: Int): Int {
    if (frameCount <= 0) return 0
    var max = 0
    val sampleCount = frameCount * SHORTS_PER_FRAME
    for (i in 0 until sampleCount) {
        val magnitude = abs(buffer[i].toInt())
        if (magnitude > max) max = magnitude
    }
    return max
}
