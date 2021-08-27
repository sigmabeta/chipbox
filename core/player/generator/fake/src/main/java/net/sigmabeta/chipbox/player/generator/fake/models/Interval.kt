package net.sigmabeta.chipbox.player.generator.fake.models

enum class Interval {
    UNISON,
    SEMITONE,
    WHOLE_TONE,
    THIRD_MINOR,
    THIRD_MAJOR,
    FOURTH_PERFECT,
    TRITONE,
    FIFTH_PERFECT,
    SIXTH_MINOR,
    SIXTH_MAJOR,
    SEVENTH_MINOR,
    SEVENTH_MAJOR,
    OCTAVE,
    NINTH_MINOR,
    NINTH_MAJOR,
    TENTH_MINOR,
    TENTH_MAJOR,
    ELEVENTH_PERFECT,
    TRITONE_OCTAVE_UP,
    FIFTH_PERFECT_OCTAVE_UP,
    TWELFTH_PERFECT,
    THIRTEENTH_MINOR,
    THIRTEENTH_MAJOR;

    fun abovePitch(original: Pitch): Pitch {
        val sumOrdinal = original.pitchClass.ordinal + ordinal

        val notesInOctave = PitchClass.values().size
        val octaveDiff = sumOrdinal / notesInOctave
        val octave = original.octave + octaveDiff

        val ordinal = sumOrdinal % notesInOctave

        return Pitch(
            PitchClass.values()[ordinal],
            octave
        )
    }
}