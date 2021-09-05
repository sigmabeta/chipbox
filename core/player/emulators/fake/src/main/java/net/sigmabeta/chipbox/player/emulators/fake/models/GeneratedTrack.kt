package net.sigmabeta.chipbox.player.emulators.fake.models

data class GeneratedTrack(
    val trackId: Long,
    val trackLengthMs: Double,
    val scale: Scale,
    val timeSignature: TimeSignature,
    val tempo: Int,
    val measures: List<Measure>
)