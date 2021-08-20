package net.sigmabeta.chipbox.models

data class Track(
    val id: Long,
    val path: String,
    val title: String,
    val trackLengthMs: Long,
    val trackNumber: Int,
    val fade: Boolean,
    val game: Game?,
    val artists: List<Artist>?
)