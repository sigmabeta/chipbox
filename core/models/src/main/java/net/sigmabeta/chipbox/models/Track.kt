package net.sigmabeta.chipbox.models

data class Track(
    val id: Long,
    val number: Int,
    val path: String,
    val title: String,
    val gameTitle: String,
    val artist: String,
    val platformName: String
)