package net.sigmabeta.chipbox.models

data class Track(
    val id: Long,
    val number: Int,
    val path: String,
    val title: String,
    val gameTitle: String,
    val artists: MutableList<Artist>?,
    var game: Game?, // TODO make this a val.
    val platformName: String
)