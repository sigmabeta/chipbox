package net.sigmabeta.chipbox.contentsource.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import net.sigmabeta.chipbox.contentsource.LibraryFileInfo
import net.sigmabeta.chipbox.contentsource.LibraryLocationInfo
import net.sigmabeta.chipbox.contentsource.LibrarySource

/**
 * Test [LibrarySource] backed by a mutable in-memory locations list. Tests can both observe the
 * `locations` StateFlow (production view-model wires up an init-time collect) and push fresh
 * values through [setLocations]; [addLibraryLocation] / [removeLibraryLocation] mutate the list
 * AND record the call (via [addedLocations] / [removedLocations]) so assertions can verify the
 * view-model dispatched the right thing.
 *
 * `openBytes` returns null and `scanFiles` returns an empty flow — neither is exercised by
 * view-model tests; they're here only because [LibrarySource] inherits them from
 * `ContentSource`.
 */
class FakeLibrarySource(
    initial: List<LibraryLocationInfo> = emptyList(),
) : LibrarySource {
    override val sourceId: String = "fake"

    private val _locations = MutableStateFlow(initial)
    override val locations: StateFlow<List<LibraryLocationInfo>> = _locations.asStateFlow()

    val addedLocations: MutableList<String> = mutableListOf()
    val removedLocations: MutableList<String> = mutableListOf()

    fun setLocations(locations: List<LibraryLocationInfo>) { _locations.value = locations }

    override suspend fun openBytes(identifier: String): ByteArray? = null
    override fun scanFiles(): Flow<LibraryFileInfo> = emptyFlow()
    override fun addLibraryLocation(identifier: String) {
        addedLocations += identifier
        _locations.update { it + LibraryLocationInfo(identifier, null) }
    }
    override fun removeLibraryLocation(identifier: String) {
        removedLocations += identifier
        _locations.update { existing -> existing.filterNot { it.identifier == identifier } }
    }
}
