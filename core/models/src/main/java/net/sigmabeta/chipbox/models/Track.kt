package net.sigmabeta.chipbox.models

data class Track(
    val id: Long,
    val number: Int,
    val path: String,
    val title: String,
    val artists: List<Artist>?,
    var game: Game?,
    val trackLengthMs: Long,
    val platformName: String
)