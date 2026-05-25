package net.sigmabeta.chipbox.player.emulators.fake.models

private const val BEATS_SIXTEENTH = 0.25
private const val BEATS_EIGHTH = 0.5
private const val BEATS_QUARTER = 1.0
private const val BEATS_QUARTER_DOTTED = 1.5
private const val BEATS_HALF = 2.0
private const val BEATS_HALF_DOTTED = 3.0
private const val BEATS_WHOLE = 4.0

private const val SECONDS_PER_MINUTE = 60.0
private const val MS_PER_SECOND = 1000.0

enum class Duration(val beats: Double) {
    SIXTEENTH(BEATS_SIXTEENTH),
    EIGHTH(BEATS_EIGHTH),
    QUARTER(BEATS_QUARTER),
    QUARTER_DOTTED(BEATS_QUARTER_DOTTED),
    HALF(BEATS_HALF),
    HALF_DOTTED(BEATS_HALF_DOTTED),
    WHOLE(BEATS_WHOLE);

    fun toMsAtTempo(tempoBpm: Int) = beats
        .div(tempoBpm)
        .times(SECONDS_PER_MINUTE)
        .times(MS_PER_SECOND)
}
