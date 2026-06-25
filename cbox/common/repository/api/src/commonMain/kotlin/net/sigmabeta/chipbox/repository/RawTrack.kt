package net.sigmabeta.chipbox.repository

import net.sigmabeta.chipbox.models.ChainFile
import net.sigmabeta.chipbox.models.Platform

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
    val platform: Platform = Platform.OTHER,
    // Optional descriptive metadata, null when the file carried none. Track-level fields describe
    // this specific subtune/rip; the game-level fields ([copyright]/[releaseDate]/[genre]/[gameTitleJp])
    // describe the release and are aggregated up into the owning [RawGame] by the scanner.
    val comment: String? = null,
    val dumper: String? = null,
    val dumpDate: String? = null,
    val titleJp: String? = null,
    val artistJp: String? = null,
    val copyright: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val gameTitleJp: String? = null,
)
