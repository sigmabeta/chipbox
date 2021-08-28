package net.sigmabeta.chipbox.player.generator.fake.models

data class GeneratedTrack(
    val trackId: Long,
    val trackLengthMs: Double,
    val notes: List<Note>
)