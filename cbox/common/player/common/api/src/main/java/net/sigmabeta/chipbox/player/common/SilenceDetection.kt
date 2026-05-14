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
