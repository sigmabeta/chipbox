package net.sigmabeta.chipbox.features.folderpicker

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import net.sigmabeta.sage.di.AppScope
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/**
 * okio-backed [FolderLister]. [fileSystem] is bound to `FileSystem.SYSTEM` in production
 * (provided by each app's DI module — Android, JVM, CLI) and to `FakeFileSystem` in tests.
 * Sorts subfolders alphabetically by lower-cased name so order is stable across filesystems;
 * child counts are a one-level peek into each subfolder so the rows can show
 * "$folderCount folders, $fileCount files". Dotfiles are filtered out unless `showHidden` is set.
 *
 * Unreadable directories (permission denied, vanished mid-listing, ...) collapse to a listing
 * flagged `readable = false` — the picker surfaces an explanatory error state rather than crashing
 * the screen, while still reporting the parent path so the user can ascend back out. This is the
 * expected case for Android's traverse-only storage parents (`/storage/emulated`, `/storage`, `/`),
 * which apps may cross but not enumerate even with All Files Access.
 */
@Inject
@ContributesBinding(AppScope::class)
class OkioFolderLister(private val fileSystem: FileSystem) : FolderLister {
    override fun list(path: String, showHidden: Boolean): FolderListing {
        val root = path.toPath()
        val children = try {
            fileSystem.list(root)
        } catch (_: IOException) {
            return FolderListing(emptyList(), 0, root.parent?.toString(), readable = false)
        }

        val visible = children.visibleUnless(showHidden)
        val folders = visible
            .filter { fileSystem.metadataOrNull(it)?.isDirectory == true }
            .sortedBy { it.name.lowercase() }
            .map { child ->
                val grandChildren = try {
                    fileSystem.list(child).visibleUnless(showHidden)
                } catch (_: IOException) {
                    emptyList()
                }
                FolderPickerEntry(
                    name = child.name,
                    path = child.toString(),
                    childFolderCount = grandChildren.count { fileSystem.metadataOrNull(it)?.isDirectory == true },
                    childFileCount = grandChildren.count { fileSystem.metadataOrNull(it)?.isRegularFile == true },
                )
            }

        return FolderListing(
            folders = folders,
            fileCount = visible.count { fileSystem.metadataOrNull(it)?.isRegularFile == true },
            parentPath = root.parent?.toString(),
        )
    }

    private fun List<Path>.visibleUnless(showHidden: Boolean) =
        if (showHidden) this else filterNot { it.name.startsWith(".") }
}
