package net.sigmabeta.chipbox.player.generator.fake.models

enum class TimeSignature(val numberOfBeats: Int, val durationOfBeat: Duration) {
    COMMON(4, Duration.QUARTER),
    MARCH(2, Duration.QUARTER),
    WALTZ(3, Duration.QUARTER),
    FIVE(5, Duration.EIGHTH),
    UNSQUARE(7, Duration.EIGHTH),
    BLUE_RONDO(9, Duration.EIGHTH);
}
