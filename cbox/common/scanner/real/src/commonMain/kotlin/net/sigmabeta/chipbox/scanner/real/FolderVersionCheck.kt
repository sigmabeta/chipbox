package net.sigmabeta.chipbox.scanner.real

import net.sigmabeta.chipbox.repository.FolderSnapshot

/**
 * Whether a stored [FolderSnapshot] was produced by the current code. A folder is up to date only
 * when all of its tracks carry the current scanner version AND the current reader version for their
 * format. Any mismatch — including the all-zero default left on rows written before versioning
 * existed — means the folder must be re-read and re-persisted.
 */
internal fun FolderSnapshot.isUpToDate(
    currentScannerVersion: Int,
    currentReaderVersionFor: (String) -> Int,
): Boolean =
    scannerVersion == currentScannerVersion &&
        readerVersions.all { (extension, stored) -> currentReaderVersionFor(extension) == stored }
