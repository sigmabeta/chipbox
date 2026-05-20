package net.sigmabeta.chipbox.contentsource

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import net.sigmabeta.sage.coroutines.SageDispatchers
import net.sigmabeta.sage.logging.Hatchet
import java.io.InputStream

class AndroidFileContentSource(
    private val context: Context,
    private val dispatchers: SageDispatchers,
    private val hatchet: Hatchet,
) : LibrarySource {

    override val sourceId: String = SOURCE_ID

    override suspend fun openBytes(identifier: String): ByteArray? =
        openInputStream(Uri.parse(identifier))?.use { it.readBytes() }

    private val _libraryLocations = MutableStateFlow<List<LibraryLocation>>(emptyList())
    val libraryLocations: StateFlow<List<LibraryLocation>> = _libraryLocations.asStateFlow()

    // LibrarySource projections — same data, platform-neutral types. The legacy SAF-typed
    // properties above stay for the Android UI that still calls `addLibraryLocation(uri)`.
    private val sourceScope = CoroutineScope(SupervisorJob() + dispatchers.disk)
    override val locations: StateFlow<List<LibraryLocationInfo>> = _libraryLocations
        .map { list -> list.map { LibraryLocationInfo(it.uri.toString(), it.displayName) } }
        .stateIn(sourceScope, SharingStarted.Eagerly, emptyList())

    override fun scanFiles(): Flow<LibraryFileInfo> = scanLibraryFiles().map { libFile ->
        LibraryFileInfo(
            identifier = libFile.uri.toString(),
            parentFolderId = libFile.parentDocumentId,
            name = libFile.name,
            extension = libFile.extension,
            mimeType = libFile.mimeType,
            sizeBytes = libFile.sizeBytes,
        )
    }

    override fun addLibraryLocation(identifier: String) {
        addLibraryLocation(Uri.parse(identifier))
    }

    fun addLibraryLocation(uri: Uri) {
        if (_libraryLocations.value.any { it.uri == uri }) {
            hatchet.d("Library location already added: $uri")
            return
        }
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val displayName = queryTreeDisplayName(uri)
        _libraryLocations.update { it + LibraryLocation(uri, displayName) }
        hatchet.i("Added library location: $uri ($displayName)")
    }

    fun scanLibraryFiles(): Flow<LibraryFile> = flow {
        for (location in _libraryLocations.value) {
            val rootDocId = DocumentsContract.getTreeDocumentId(location.uri)
            walk(location.uri, rootDocId)
        }
    }.flowOn(dispatchers.disk)

    suspend fun openInputStream(uri: Uri): InputStream? = withContext(dispatchers.disk) {
            runCatching { context.contentResolver.openInputStream(uri) }
                .onFailure { hatchet.w("Failed to open $uri: $it") }
                .getOrNull()
        }

    private suspend fun FlowCollector<LibraryFile>.walk(treeUri: Uri, docId: String) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2)
                val size = if (cursor.isNull(COLUMN_INDEX_SIZE)) 0L else cursor.getLong(COLUMN_INDEX_SIZE)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(treeUri, childDocId)
                } else {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                    emit(
                        LibraryFile(
                            uri = fileUri,
                            parentDocumentId = docId,
                            name = name,
                            extension = name.substringAfterLast('.', "").lowercase(),
                            mimeType = mime,
                            sizeBytes = size,
                        )
                    )
                }
            }
        }
    }

    private fun queryTreeDisplayName(treeUri: Uri): String? {
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        return context.contentResolver.query(
            rootDocUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    companion object {
        const val SOURCE_ID = "android-file"

        private const val COLUMN_INDEX_SIZE = 3
    }
}
