package net.sigmabeta.chipbox.contentsource

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-neutral library access. A [LibrarySource] is a [ContentSource] that also knows
 * what locations the user has added and how to walk them for music files. Identifiers are
 * absolute filesystem paths on both Android and the JVM (both targets use the raw-path
 * `LocalFileContentSource`), and the same strings flow into [Track.path] / the scanner's track
 * records, so the player's [ContentSource.openBytes] resolves them later without knowing which
 * platform produced them.
 *
 * Existed to decouple the shared `RealScanner` from the concrete content-source impl.
 */
interface LibrarySource : ContentSource {
    val locations: StateFlow<List<LibraryLocationInfo>>

    /**
     * Walk the configured [locations] and stream the music folders found, each as a complete
     * [LibraryFolderInfo] (a folder and all of its direct files). Emitting a folder the moment it's
     * fully discovered — rather than draining the whole walk first — lets the scanner read each
     * folder while later folders are still being discovered.
     */
    fun scanFolders(): Flow<LibraryFolderInfo>

    /**
     * Persist a new library root identified by [identifier] (an absolute filesystem path).
     * Implementations decide where to persist the location list across restarts (a small text
     * file alongside the app's data).
     */
    fun addLibraryLocation(identifier: String)

    /**
     * The inverse of [addLibraryLocation]: drop the library root identified by [identifier] and
     * remove it from [locations]. A no-op if [identifier] isn't a current location. Does not
     * delete already-imported tracks — a rescan prunes those.
     */
    fun removeLibraryLocation(identifier: String)
}
