package net.sigmabeta.chipbox.models

data class Track(
    val id: Long,
    val path: String,
    val title: String,
    val trackLengthMs: Long,
    var game: Game?,
    val artists: List<Artist>?
)