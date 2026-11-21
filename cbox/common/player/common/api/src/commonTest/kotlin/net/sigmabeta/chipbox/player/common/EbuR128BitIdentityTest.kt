package net.sigmabeta.chipbox.player.common

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the buffer-wise [EbuR128] to the frame-by-frame measurer it replaced ([PerSampleEbuR128]).
 * Both read-outs are compared bit for bit after every buffer, across rates, channel counts and
 * buffer sizes from a single frame up, because the PCM cache header stores them.
 */
class EbuR128BitIdentityTest {

    @Test
    fun `loudness and true peak are bit-identical to the per-sample measurer`() {
        val random = Random(SEED)
        var trial = 0
        for (sampleRate in SAMPLE_RATES) {
            for (channels in 1..2) {
                val measurer = EbuR128(sampleRate, channels)
                val reference = PerSampleEbuR128(sampleRate, channels)
                val frames = sampleRate * SECONDS
                val pcm = segmentedSignal(random, frames, channels, sampleRate)
                val buffer = ShortArray(MAX_BUFFER_FRAMES * channels)
                var f = 0
                while (f < frames) {
                    val count = minOf(frames - f, 1 + random.nextInt(MAX_BUFFER_FRAMES))
                    pcm.copyInto(buffer, 0, f * channels, (f + count) * channels)
                    measurer.process(buffer, count)
                    reference.process(buffer, count)
                    f += count
                    val where = "trial $trial ($sampleRate Hz, $channels ch), frame $f"
                    assertEquals(
                        reference.integratedLoudness().toRawBits(),
                        measurer.integratedLoudness().toRawBits(),
                        "LUFS at $where",
                    )
                    assertEquals(
                        reference.truePeakDbtp().toRawBits(),
                        measurer.truePeakDbtp().toRawBits(),
                        "dBTP at $where",
                    )
                }
                trial++
            }
        }
    }

    private companion object {
        const val SEED = 0xEB0128
        val SAMPLE_RATES = intArrayOf(32_000, 44_100, 48_000)
        const val SECONDS = 4
        const val MAX_BUFFER_FRAMES = 8_192
    }
}
