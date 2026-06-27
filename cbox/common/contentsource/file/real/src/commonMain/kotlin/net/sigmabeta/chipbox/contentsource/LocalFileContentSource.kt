package net.sigmabeta.chipbox.contentsource

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * okio-backed [LibrarySource]: identifies files and library roots by absolute filesystem path,
 * doing all I/O through an injected [FileSystem] (`FileSystem.SYSTEM` in production, a
 * `FakeFileSystem` in tests). Lives in `commonMain` so every target — the JVM/desktop app, the
 * Android app, the headless CLI/server — shares one walker (the shared `RealScanner` drives any of
 * them). Android dropped SAF/`DocumentsContract` in favour of `MANAGE_EXTERNAL_STORAGE` + raw
 * paths, so it uses this same path-based source.
 *
 * Library locations are persisted as newline-separated absolute paths in [locationsFile] (there's
 * no SAF persisted-permission store to lean on); each platform's DI picks the file location
 * (`workDir` on the JVM, `context.filesDir` on Android).
 */
internal const val SOURCE_ID = "file"

class LocalFileContentSource(
    private val fileSystem: FileSystem,
    // Newline-separated absolute paths of the user's library locations, persisted across runs.
    private val locationsFile: Path,
) : LibrarySource {
    override val sourceId: String = SOURCE_ID

    private val _locations = MutableStateFlow(readPersistedLocations())
    override val locations: StateFlow<List<LibraryLocationInfo>> = _locations.asStateFlow()

    fun addLocation(dir: Path) {
        require(fileSystem.metadataOrNull(dir)?.isDirectory == true) { "Not a directory: $dir" }
        addLibraryLocation(dir.toString())
    }

    override fun addLibraryLocation(identifier: String) {
        val path = identifier.toPath()
        require(fileSystem.metadataOrNull(path)?.isDirectory == true) { "Not a directory: $identifier" }
        val info = LibraryLocationInfo(path.toString(), path.name)
        _locations.update { current ->
            if (current.any { it.identifier == info.identifier }) current else current + info
        }
        persistLocations()
    }

    /** Drops the saved location with this absolute-path [identifier], persisting the change. */
    override fun removeLibraryLocation(identifier: String) {
        _locations.update { current -> current.filterNot { it.identifier == identifier } }
        persistLocations()
    }

    private fun readPersistedLocations(): List<LibraryLocationInfo> =
        if (fileSystem.metadataOrNull(locationsFile)?.isRegularFile == true) {
            fileSystem.read(locationsFile) { readUtf8() }
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { path -> LibraryLocationInfo(path, path.toPath().name) }
        } else {
            emptyList()
        }

    private fun persistLocations() {
        runCatching {
            locationsFile.parent?.let { fileSystem.createDirectories(it) }
            fileSystem.write(locationsFile) {
                writeUtf8(_locations.value.joinToString("\n") { it.identifier })
            }
        }
    }

    override fun scanFolders(): Flow<LibraryFolderInfo> = flow {
        for (loc in _locations.value) {
            emitFoldersIn(loc.identifier.toPath())
        }
    }

    // Depth-first walk that emits each directory's own files as one complete [LibraryFolderInfo] the
    // moment that directory is enumerated, then descends into its subdirectories. Streaming whole
    // folders (rather than draining the entire tree first) is what lets the scanner read a folder
    // while later folders are still being discovered. A directory with no direct files emits nothing
    // but is still descended into. Hidden subdirectories (names starting with ".") are skipped.
    private suspend fun FlowCollector<LibraryFolderInfo>.emitFoldersIn(dir: Path) {
        val entries = fileSystem.listOrNull(dir) ?: return
        val withMetadata = entries.map { it to fileSystem.metadataOrNull(it) }
        val files = withMetadata.mapNotNull { (entry, metadata) ->
            if (metadata?.isRegularFile == true) entry.toLibraryFileInfo(dir, metadata) else null
        }
        if (files.isNotEmpty()) {
            emit(LibraryFolderInfo(folderId = dir.toString(), files = files))
        }
        for ((entry, metadata) in withMetadata) {
            if (metadata?.isDirectory == true && !entry.name.startsWith(".")) emitFoldersIn(entry)
        }
    }

    private fun Path.toLibraryFileInfo(parent: Path, metadata: FileMetadata) = LibraryFileInfo(
        identifier = toString(),
        parentFolderId = parent.toString(),
        name = name,
        extension = name.substringAfterLast('.', "").lowercase(),
        mimeType = null,
        sizeBytes = metadata.size ?: 0L,
        lastModifiedMs = metadata.lastModifiedAtMillis ?: 0L,
    )

    override suspend fun openBytes(identifier: String): ByteArray? {
        val path = identifier.toPath()
        return if (fileSystem.metadataOrNull(path)?.isRegularFile == true) {
            fileSystem.read(path) { readByteArray() }
        } else {
            null
        }
    }
}
