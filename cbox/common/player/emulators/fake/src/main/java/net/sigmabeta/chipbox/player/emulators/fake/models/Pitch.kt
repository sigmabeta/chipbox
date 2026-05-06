package net.sigmabeta.chipbox.player.emulators.fake.models

import kotlin.math.pow

data class Pitch(
    val pitchClass: PitchClass,
    val octave: Int
) {
    val frequency = pitchClass.frequency * 2.toDouble().pow(octave.toDouble())
}