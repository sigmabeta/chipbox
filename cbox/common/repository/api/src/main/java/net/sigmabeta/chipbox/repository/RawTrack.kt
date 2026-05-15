package net.sigmabeta.chipbox.repository

import net.sigmabeta.chipbox.models.ChainFile

data class RawTrack(
    val path: String,
    val source: String,
    val title: String,
    val artist: String,
    val game: String,
    val length: Long,
    val trackNumber: Int,
    val fadeLengthMs: Long,
    val chainFiles: List<ChainFile> = emptyList(),
    val extension: String = "",
)