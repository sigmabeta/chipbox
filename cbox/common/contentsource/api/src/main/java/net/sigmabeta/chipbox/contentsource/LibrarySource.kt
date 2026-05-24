package net.sigmabeta.chipbox.contentsource

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-neutral library access. A [LibrarySource] is a [ContentSource] that also knows
 * what locations the user has added and how to walk them for music files. Identifiers are
 * platform-specific strings — for the Android impl, the SAF tree-doc URI string; for the JVM
 * impl, an absolute filesystem path — and the same strings flow into [Track.path] / the
 * scanner's track records, so the player's [ContentSource.openBytes] resolves them later
 * without knowing which platform produced them.
 *
 * Existed to decouple the shared `RealScanner` from `AndroidFileContentSource`'s concrete
 * SAF type: roadmap item 4 in `docs/kmp-migration.md`.
 */
interface LibrarySource : ContentSource {
    val locations: StateFlow<List<LibraryLocationInfo>>

    fun scanFiles(): Flow<LibraryFileInfo>

    /**
     * Persist a new library root identified by [identifier]. Same string-shape contract as
     * [locations] / [scanFiles]: the SAF tree-doc URI string on Android, an absolute filesystem
     * path on the JVM. Implementations decide how to take a content lock on the location
     * (e.g. `ContentResolver.takePersistableUriPermission` on Android) and where to persist
     * the location list across restarts.
     */
    fun addLibraryLocation(identifier: String)

    /**
     * The inverse of [addLibraryLocation]: drop the library root identified by [identifier],
     * remove it from [locations], and release any content lock taken when it was added (e.g.
     * `ContentResolver.releasePersistableUriPermission` on Android). A no-op if [identifier]
     * isn't a current location. Does not delete already-imported tracks — a rescan prunes those.
     */
    fun removeLibraryLocation(identifier: String)
}
