package net.sigmabeta.chipbox.player.emulators.fake.models

data class Scale(
    val root: PitchClass,
    val mode: ScaleMode
) {
    fun note(degree: Int, startingOctave: Int = 4): Pitch {
        val interval = mode.intervals[degree]
        val rootPitch = Pitch(root, startingOctave)

        return interval.abovePitch(rootPitch)
    }
}