package net.sigmabeta.chipbox.contentsource

/**
 * One source folder the scanner discovered, with all of its direct files. The scanner reads a folder
 * as a unit — folder signature, PSF `_lib` chain resolution, m3u overlays, and the one-game-per-folder
 * collapse all need the complete file set — so [LibrarySource.scanFolders] streams whole folders
 * rather than individual files: each emission is ready to read, letting discovery of later folders
 * overlap reading of earlier ones. [folderId] is the [LibraryFileInfo.parentFolderId] every file here
 * shares.
 */
data class LibraryFolderInfo(
    val folderId: String,
    val files: List<LibraryFileInfo>,
)
