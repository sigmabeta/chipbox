package net.sigmabeta.chipbox.organizer

import okio.Path

/** One folder's worth of files to relocate, from [source] into [destination]. */
data class FolderMove(
    val category: String,
    val folderName: String,
    val source: Path,
    val destination: Path,
    val entries: List<Path>,
    val sourceIsRoot: Boolean,
)
