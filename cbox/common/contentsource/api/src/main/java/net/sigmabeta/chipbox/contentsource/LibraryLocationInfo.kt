package net.sigmabeta.chipbox.contentsource

/**
 * One library location the user added. [identifier] mirrors [LibraryFileInfo.identifier]'s
 * shape — a SAF tree URI string on Android, an absolute path on JVM — so a [LibrarySource]
 * round-trips locations through the same strings its files use.
 */
data class LibraryLocationInfo(
    val identifier: String,
    val displayName: String?,
)
