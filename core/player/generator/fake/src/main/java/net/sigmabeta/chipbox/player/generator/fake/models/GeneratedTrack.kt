package net.sigmabeta.chipbox.player.generator.fake.models

data class GeneratedTrack(
    val trackId: Long,
    val trackLengthMs: Double,
    val scale: Scale,
//    val timeSignature: TimeSignature,
    val tempo: Int,
    val notes: List<Note>
)