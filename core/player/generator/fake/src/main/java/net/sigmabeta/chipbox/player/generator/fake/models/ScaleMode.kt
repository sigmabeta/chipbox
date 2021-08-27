package net.sigmabeta.chipbox.player.generator.fake.models

enum class ScaleMode(val intervals: List<Interval>) {
    MAJOR_IONIAN(
        listOf(
            Interval.UNISON,
            Interval.WHOLE_TONE,
            Interval.THIRD_MAJOR,
            Interval.FOURTH_PERFECT,
            Interval.FIFTH_PERFECT,
            Interval.SIXTH_MAJOR,
            Interval.SEVENTH_MAJOR
        )
    ),

    MINOR_DORIAN(
        listOf(
            Interval.UNISON,
            Interval.WHOLE_TONE,
            Interval.THIRD_MINOR,
            Interval.FOURTH_PERFECT,
            Interval.FIFTH_PERFECT,
            Interval.SIXTH_MAJOR,
            Interval.SEVENTH_MINOR
        )
    ),

    PHRYGIAN(
        listOf(
            Interval.UNISON,
            Interval.SEMITONE,
            Interval.THIRD_MINOR,
            Interval.FOURTH_PERFECT,
            Interval.FIFTH_PERFECT,
            Interval.SIXTH_MINOR,
            Interval.SEVENTH_MINOR
        )
    ),

    LYDIAN(
        listOf(
            Interval.UNISON,
            Interval.WHOLE_TONE,
            Interval.THIRD_MAJOR,
            Interval.TRITONE,
            Interval.FIFTH_PERFECT,
            Interval.SIXTH_MAJOR,
            Interval.SEVENTH_MAJOR
        )
    ),

    DOMINANT_MIXOLYDIAN(
        listOf(
            Interval.UNISON,
            Interval.WHOLE_TONE,
            Interval.THIRD_MAJOR,
            Interval.FOURTH_PERFECT,
            Interval.FIFTH_PERFECT,
            Interval.SIXTH_MAJOR,
            Interval.SEVENTH_MINOR
        )
    ),

    MINOR_AEOLIAN(
        listOf(
            Interval.UNISON,
            Interval.WHOLE_TONE,
            Interval.THIRD_MINOR,
            Interval.FOURTH_PERFECT,
            Interval.FIFTH_PERFECT,
            Interval.SIXTH_MINOR,
            Interval.SEVENTH_MINOR
        )
    );
}