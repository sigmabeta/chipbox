package net.sigmabeta.chipbox.player.common

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Interleaved 16-bit test audio built from segments of random length and level: digital silence,
 * quiet, mid and near-full-scale noise-plus-tone, with an occasional single full-scale click.
 *
 * It opens with a click, so the true-peak running maximum is high early and the quiet stretches that
 * follow take the skip path; the louder segments later must still take the full filter. That puts
 * both paths, and the switch between them inside a buffer, into every trial.
 */
internal fun segmentedSignal(random: Random, frames: Int, channels: Int, sampleRate: Int): ShortArray {
    val out = ShortArray(frames * channels)
    var f = 0
    var phase = 0.0
    val levels = doubleArrayOf(0.0, QUIET, MID, LOUD)
    while (f < frames) {
        val length = minOf(frames - f, 1 + random.nextInt(MAX_SEGMENT_FRAMES))
        val level = levels[random.nextInt(levels.size)]
        val tone = TONE_MIN_HZ + random.nextDouble() * TONE_SPAN_HZ
        for (i in 0 until length) {
            phase += 2.0 * PI * tone / sampleRate
            for (c in 0 until channels) {
                val noise = random.nextDouble() * 2.0 - 1.0
                val v = level * (TONE_SHARE * sin(phase + c) + (1.0 - TONE_SHARE) * noise)
                out[(f + i) * channels + c] = (v * Short.MAX_VALUE).toInt().toShort()
            }
        }
        val clickAt = when {
            f == 0 -> 0
            random.nextInt(CLICK_ODDS) == 0 -> random.nextInt(length)
            else -> -1
        }
        if (clickAt >= 0) out[(f + clickAt) * channels + random.nextInt(channels)] = Short.MIN_VALUE
        f += length
    }
    return out
}

private const val QUIET = 0.01
private const val MID = 0.2
private const val LOUD = 0.9
private const val MAX_SEGMENT_FRAMES = 6_000
private const val TONE_MIN_HZ = 60.0
private const val TONE_SPAN_HZ = 8_000.0
private const val TONE_SHARE = 0.7
private const val CLICK_ODDS = 8
