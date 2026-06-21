package net.sigmabeta.chipbox.contentsource

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import net.sigmabeta.chipbox.contentsource.LibraryFileInfo
import net.sigmabeta.chipbox.contentsource.LibraryLocationInfo
import net.sigmabeta.chipbox.contentsource.LibrarySource
import java.io.File

/**
 * Raw `java.io.File` [LibrarySource]: identifies files and library roots by absolute filesystem
 * path. Lives in jvmSharedMain (`src/main/java`) so both the JVM/desktop app and the Android app use
 * it — Android dropped SAF/`DocumentsContract` in favour of `MANAGE_EXTERNAL_STORAGE` + raw paths,
 * so the two targets now share one walker (the shared `RealScanner` drives either).
 *
 * Library locations are persisted as newline-separated absolute paths in [locationsFile] (there's
 * no SAF persisted-permission store to lean on); each platform's DI picks the file location
 * (`workDir` on the JVM, `context.filesDir` on Android).
 */
internal const val SOURCE_ID = "file"

class LocalFileContentSource(
    // Newline-separated absolute paths of the user's library locations, persisted across runs.
    private val locationsFile: File,
) : LibrarySource {
    override val sourceId: String = SOURCE_ID

    private val _locations = MutableStateFlow(readPersistedLocations())
    override val locations: StateFlow<List<LibraryLocationInfo>> = _locations.asStateFlow()

    fun addLocation(dir: File) {
        require(dir.isDirectory) { "Not a directory: ${dir.absolutePath}" }
        addLibraryLocation(dir.absolutePath)
    }

    override fun addLibraryLocation(identifier: String) {
        val file = File(identifier)
        require(file.isDirectory) { "Not a directory: $identifier" }
        val info = LibraryLocationInfo(file.absolutePath, file.name)
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
        if (locationsFile.isFile) {
            locationsFile.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { path -> LibraryLocationInfo(path, File(path).name) }
        } else {
            emptyList()
        }

    private fun persistLocations() {
        runCatching {
            locationsFile.parentFile?.mkdirs()
            locationsFile.writeText(_locations.value.joinToString("\n") { it.identifier })
        }
    }

    override fun scanFiles(): Flow<LibraryFileInfo> = flow {
        for (loc in _locations.value) {
            val root = File(loc.identifier)
            for (file in root.walkTopDown()) {
                if (!file.isFile) continue
                val parent = file.parentFile?.absolutePath ?: root.absolutePath
                emit(
                    LibraryFileInfo(
                        identifier = file.absolutePath,
                        parentFolderId = parent,
                        name = file.name,
                        extension = file.extension.lowercase(),
                        mimeType = null,
                        sizeBytes = file.length(),
                        lastModifiedMs = file.lastModified(),
                    ),
                )
            }
        }
    }

    override suspend fun openBytes(identifier: String): ByteArray? {
        val file = File(identifier)
        return if (file.isFile) file.readBytes() else null
    }
}
