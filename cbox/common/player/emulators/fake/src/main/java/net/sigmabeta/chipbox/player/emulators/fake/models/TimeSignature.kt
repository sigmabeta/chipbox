package net.sigmabeta.chipbox.player.emulators.fake.models

private const val BEATS_COMMON = 4
private const val BEATS_WALTZ = 3
private const val BEATS_FIVE = 5
private const val BEATS_UNSQUARE = 7
private const val BEATS_BLUE_RONDO = 9

enum class TimeSignature(val numberOfBeats: Int, val durationOfBeat: Duration) {
    COMMON(BEATS_COMMON, Duration.QUARTER),
    MARCH(2, Duration.QUARTER),
    WALTZ(BEATS_WALTZ, Duration.QUARTER),
    FIVE(BEATS_FIVE, Duration.EIGHTH),
    UNSQUARE(BEATS_UNSQUARE, Duration.EIGHTH),
    BLUE_RONDO(BEATS_BLUE_RONDO, Duration.EIGHTH)
}
