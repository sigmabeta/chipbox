package net.sigmabeta.chipbox.features.folderpicker

/**
 * Reads the immediate contents of [path] and projects them into [FolderPickerEntry] rows plus
 * the aggregate file count. Abstracted so the view model stays testable without touching disk.
 * Production binding ([OkioFolderLister]) lives in commonMain and uses okio's `FileSystem`.
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
 *
 * [readable] is false when the directory couldn't be enumerated at all (permission denied,
 * vanished mid-listing, ...) — distinct from a genuinely empty directory, which is [readable] with
 * no [folders]. On Android this is the common case for the traverse-only parents above
 * `/storage/emulated/0` (`/storage/emulated`, `/storage`, `/`), which apps may cross but not list;
 * the picker surfaces an explanatory error state rather than a silently-empty screen.
 */
data class FolderListing(
    val folders: List<FolderPickerEntry>,
    val fileCount: Int,
    val parentPath: String? = null,
    val readable: Boolean = true,
)
