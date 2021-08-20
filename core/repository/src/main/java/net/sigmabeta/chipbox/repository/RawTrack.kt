package net.sigmabeta.chipbox.repository

data class RawTrack(
    val path: String,
    val title: String,
    val artist: String,
    val game: String,
    val length: Long,
    val trackNumber: Int,
    val fade: Boolean
)