package net.sigmabeta.chipbox.readers

data class RawTrack(
    val path: String,
    val title: String,
    val artist: String,
    val game: String,
    val length: Long,
    val platform: String
)