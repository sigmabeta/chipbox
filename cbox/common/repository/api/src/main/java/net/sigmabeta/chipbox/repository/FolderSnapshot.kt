package net.sigmabeta.chipbox.repository

/**
 * A pre-scan view of one already-stored game, keyed by [RawGame.folderKey] in
 * [Repository.folderSnapshots]. The scanner compares [signature] against a freshly computed folder
 * hash to decide whether to skip re-reading the folder, and uses [trackCount] to keep the scan's
 * progress totals accurate for skipped folders.
 */
data class FolderSnapshot(
    val signature: String,
    val trackCount: Int,
)
