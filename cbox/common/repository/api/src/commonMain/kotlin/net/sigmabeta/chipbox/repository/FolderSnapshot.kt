package net.sigmabeta.chipbox.repository

/**
 * A pre-scan view of one already-stored game, keyed by [RawGame.folderKey] in
 * [Repository.folderSnapshots]. The scanner compares [signature] against a freshly computed folder
 * hash to decide whether to skip re-reading the folder, and uses [trackCount] to keep the scan's
 * progress totals accurate for skipped folders.
 *
 * [scannerVersion] and [readerVersions] extend that decision: a folder is skipped only when its
 * signature matches *and* every stored track was produced by the current scanner + reader logic.
 * A version mismatch (including the all-zero default from pre-versioning rows) forces a re-read.
 */
data class FolderSnapshot(
    val signature: String,
    val trackCount: Int,
    // Scanner logic version stamped on the folder's tracks; 0 means "scanned before versioning".
    val scannerVersion: Int = 0,
    // Reader logic version keyed by file extension, so a change to one format's reader only
    // invalidates the folders that actually contain that format. A track with no dedicated reader
    // (e.g. vgmstream streamed audio) contributes 0 under its extension.
    val readerVersions: Map<String, Int> = emptyMap(),
)
