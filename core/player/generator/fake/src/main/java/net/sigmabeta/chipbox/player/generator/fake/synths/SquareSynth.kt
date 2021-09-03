package net.sigmabeta.chipbox.player.generator.fake.synths

import net.sigmabeta.chipbox.player.common.toShortValue

class SquareSynth(val dutyCycle: Double) : Synth {

    override fun generate(timeMillis: Double, frequency: Double, amplitude: Double): Short {
        val wavelength = 1000 / frequency
        val edgeThreshold = wavelength * dutyCycle
        val wavePosition = timeMillis % wavelength

        val scalar = if (wavePosition >= edgeThreshold)
            Short.MAX_VALUE
        else
            Short.MAX_VALUE.unaryMinus().toShort()

        val amplitudeShort = amplitude
            .times(scalar)
            .toShortValue()

        return if (wavePosition >= edgeThreshold) amplitudeShort else amplitudeShort
    }
}