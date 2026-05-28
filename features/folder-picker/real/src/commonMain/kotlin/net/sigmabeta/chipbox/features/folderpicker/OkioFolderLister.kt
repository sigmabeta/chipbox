package net.sigmabeta.chipbox.features.folderpicker

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import net.sigmabeta.sage.di.AppScope
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath

/**
 * okio-backed [FolderLister]. [fileSystem] is bound to `FileSystem.SYSTEM` in production
 * (provided by each app's DI module — Android, JVM, CLI) and to `FakeFileSystem` in tests.
 * Sorts subfolders alphabetically by lower-cased name so order is stable across filesystems;
 * child counts are a one-level peek into each subfolder so the rows can show
 * "$folderCount folders, $fileCount files".
 *
 * Unreadable directories (permission denied, vanished mid-listing, ...) collapse to an empty
 * listing — the picker just shows "no subfolders" rather than crashing the screen.
 */
@Inject
@ContributesBinding(AppScope::class)
class OkioFolderLister(private val fileSystem: FileSystem) : FolderLister {
    override fun list(path: String): FolderListing {
        val root = path.toPath()
        val children = try {
            fileSystem.list(root)
        } catch (_: IOException) {
            return FolderListing(emptyList(), 0)
        }

        val visible = children.filterNot { it.name.startsWith(".") }
        val folders = visible
            .filter { fileSystem.metadataOrNull(it)?.isDirectory == true }
            .sortedBy { it.name.lowercase() }
            .map { child ->
                val grandChildren = try {
                    fileSystem.list(child).filterNot { it.name.startsWith(".") }
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
        )
    }
}
