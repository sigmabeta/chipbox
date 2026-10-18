package net.sigmabeta.chipbox.js.inert

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import net.sigmabeta.chipbox.contentsource.LibraryFolderInfo
import net.sigmabeta.chipbox.contentsource.LibraryLocationInfo
import net.sigmabeta.chipbox.contentsource.LibrarySource

/**
 * The browser has no library roots to list: the server owns the single `CHIPBOX_LIBRARY_DIR`, and
 * track bytes are fetched through `HttpContentSource`. This inert [LibrarySource] closes the graph
 * for the shared view models that still inject one (Home, Settings, Manage Library, Folder Picker)
 * while reporting no folders, so nothing implies a browser-side library exists.
 *
 * It is deliberately *not* a fake test double: it never mutates, never scans, and never opens bytes.
 * [net.sigmabeta.chipbox.capabilities.AppCapabilities.canManageLibrary] = false keeps every port of
 * entry out of the UI.
 */
class EmptyLibrarySource : LibrarySource {
    override val sourceId: String = "server"
    override val locations: StateFlow<List<LibraryLocationInfo>> =
        MutableStateFlow<List<LibraryLocationInfo>>(emptyList()).asStateFlow()

    override suspend fun openBytes(identifier: String): ByteArray? = null

    override fun scanFolders(): Flow<LibraryFolderInfo> = emptyFlow()

    override fun addLibraryLocation(identifier: String) = Unit

    override fun removeLibraryLocation(identifier: String) = Unit
}
