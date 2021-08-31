package net.sigmabeta.chipbox.player.generator.fake.models

enum class Duration(val beats: Double) {
    SIXTEENTH(0.25),
    EIGHTH(0.5),
    QUARTER(1.0),
    QUARTER_DOTTED(1.5),
    HALF(2.0),
    HALF_DOTTED(3.0),
    WHOLE(4.0);

    fun toMsAtTempo(tempoBpm: Int) = beats
        .div(tempoBpm)
        .times(60.0)
        .times(1000.0)

}