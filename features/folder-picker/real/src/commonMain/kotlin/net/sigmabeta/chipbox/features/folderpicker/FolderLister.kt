package net.sigmabeta.chipbox.features.folderpicker

/**
 * Reads the immediate contents of [path] and projects them into [FolderPickerEntry] rows plus
 * the aggregate file count. Abstracted so the view model stays testable without touching disk.
 * Production binding (`JvmFolderLister`) lives in jvmSharedMain and uses `java.io.File`.
 *
 * [showHidden] controls whether dotfile entries (and dotfile grand-children in the per-row counts)
 * are included; it defaults to false so the picker hides them unless the user toggles them on.
 */
interface FolderLister {
    fun list(path: String, showHidden: Boolean = false): FolderListing
}

/**
 * The result of listing a directory: subfolders to show as rows (each with its own count of
 * grand-children), plus the total number of files directly inside (rolled up into a single row
 * per the spec — files aren't displayed individually). [parentPath] is the directory one level up,
 * or null when [path] is a filesystem root and there's nowhere to ascend to.
 */
data class FolderListing(
    val folders: List<FolderPickerEntry>,
    val fileCount: Int,
    val parentPath: String? = null,
)
