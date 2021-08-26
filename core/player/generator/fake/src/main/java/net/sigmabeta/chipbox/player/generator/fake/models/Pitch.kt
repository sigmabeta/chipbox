package net.sigmabeta.chipbox.player.generator.fake.models

import kotlin.math.pow

data class Pitch(
    val baseFreq: BaseFreq,
    val octave: Int
) {
    val frequency = baseFreq.frequency * 2.toDouble().pow(octave.toDouble())
}