package net.sigmabeta.chipbox.features.folderpicker

/**
 * Reads the immediate contents of [path] and projects them into [FolderPickerEntry] rows plus
 * the aggregate file count. Abstracted so the view model stays testable without touching disk.
 * Production binding (`JvmFolderLister`) lives in jvmSharedMain and uses `java.io.File`.
 */
interface FolderLister {
    fun list(path: String): FolderListing
}

/**
 * The result of listing a directory: subfolders to show as rows (each with its own count of
 * grand-children), plus the total number of files directly inside (rolled up into a single row
 * per the spec — files aren't displayed individually).
 */
data class FolderListing(
    val folders: List<FolderPickerEntry>,
    val fileCount: Int,
)
