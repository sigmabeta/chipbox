package net.sigmabeta.chipbox.contentsource

/**
 * One file the scanner encountered. [identifier] is the platform-specific string the
 * scanner stamps onto track records and passes back to [ContentSource.openBytes] later
 * (SAF tree-doc URI string on Android, absolute filesystem path on JVM). [parentFolderId]
 * is the scanner's grouping key — files sharing a parent end up in the same `RawGame`.
 */
data class LibraryFileInfo(
    val identifier: String,
    val parentFolderId: String,
    val name: String,
    val extension: String,
    val mimeType: String?,
    val sizeBytes: Long,
    // Last-modified time (epoch ms, 0 if the source can't report it). Combined with [sizeBytes] it
    // forms the per-file signature the scanner hashes to detect unchanged folders and skip them.
    val lastModifiedMs: Long,
)
