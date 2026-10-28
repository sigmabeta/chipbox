package net.sigmabeta.chipbox.browserstate

import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.persistence.SessionSnapshot

/**
 * Keep only the tracks the current library still knows before a saved session is restored.
 *
 * Browser state persists track ids, and RSN extraction (which re-keys tracks by extracted path) or
 * a rebuilt library can invalidate some of them. Restoring a session that names a missing track
 * would leave the director pointing at a track it cannot load, so:
 *
 * - the saved queue keeps its order, minus unknown ids;
 * - the current track stays if it survived, otherwise the track at the same index (clamped to the
 *   end) becomes current, so "resume where I was" stays as close as the surviving queue allows;
 * - a single-track session whose only track vanished is dropped;
 * - a snapshot with nothing usable (no queue and no known current track) returns null, telling the
 *   caller to clear it. Legacy snapshots without a queue are kept while their current track exists.
 *
 * Never throws: it is pure, and the caller decides when to clear the store.
 */
fun SessionSnapshot.withKnownTracks(knownTrackIds: Set<Long>): SessionSnapshot? {
    val resolvedQueue = resolvedSetlist?.takeIf { it.isNotEmpty() }
    val queue = resolvedQueue ?: explicitSetlist ?: emptyList()
    val hasQueue = resolvedQueue != null || explicitSetlist != null
    val oldIndex = currentTrackId?.let { id -> queue.indexOfFirst { it == id }.takeIf { it >= 0 } }
    val surviving = queue.filter { it in knownTrackIds }

    val knownCurrent = currentTrackId?.takeIf { it in knownTrackIds }
    val singleTrackId = contentId.takeIf { type == SessionType.SINGLE_TRACK }
    val remappedCurrent = knownCurrent
        ?: oldIndex?.let { surviving.getOrNull(it.coerceAtMost(surviving.lastIndex)) }
        ?: surviving.firstOrNull()
        ?: singleTrackId?.takeIf { it in knownTrackIds }

    if (!hasQueue && remappedCurrent == null) return null
    if (surviving.isEmpty() && remappedCurrent == null) return null

    return copy(
        resolvedSetlist = resolvedQueue?.let { surviving },
        explicitSetlist = explicitSetlist?.let { surviving },
        currentTrackId = remappedCurrent,
    )
}

/** Every track id a snapshot might reference; the caller resolves these in one repository read. */
fun SessionSnapshot.referencedTrackIds(): List<Long> = buildSet {
    addAll(resolvedSetlist.orEmpty())
    addAll(explicitSetlist.orEmpty())
    currentTrackId?.let(::add)
    if (type == SessionType.SINGLE_TRACK) add(contentId)
}.toList()
