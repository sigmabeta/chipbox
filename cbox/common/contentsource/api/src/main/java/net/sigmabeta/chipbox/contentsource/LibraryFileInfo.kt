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
)
