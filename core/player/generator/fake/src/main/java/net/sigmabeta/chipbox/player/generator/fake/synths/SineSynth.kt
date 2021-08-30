package net.sigmabeta.chipbox.player.generator.fake.synths

import net.sigmabeta.chipbox.player.common.rateInMillis
import net.sigmabeta.chipbox.player.common.toShortValue
import kotlin.math.PI
import kotlin.math.sin

object SineSynth : Synth {
    override fun generate(timeMillis: Double, frequency: Double, amplitude: Double) =
        sineValueAtMillis(
            timeMillis,
            frequency,
            amplitude
        )

    private fun sineValueAtMillis(timeMillis: Double, frequency: Double, amplitude: Double) =
        frequency.rateInMillis()        // Frequency in cycles per millis
            .times(timeMillis)          // Time in millis
            .times(SCALE_FACTOR)        // 2 PI
            .let { sin(it) }            // Sample a sine wave at the radian value calculated above
            .times(amplitude)           // Set volume
            .times(Short.MAX_VALUE)     // Convert to range of +/- 0 to 32768
            .toShortValue()             // Round to a short integer


    private const val SCALE_FACTOR = 2 * PI
}