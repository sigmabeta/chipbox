package net.sigmabeta.chipbox.player.persistence.real

import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.persistence.SessionSnapshot

/**
 * Build the [Session] the director should resume from. The saved track id becomes
 * `startingTrackId` — the director's highest-priority starting hint — so the right track resumes
 * even for a shuffled session whose order is re-rolled on restore.
 */
internal fun SessionSnapshot.toSession(): Session = Session(
    type = type,
    contentId = contentId,
    // The saved play order (when present) is carried as the explicit setlist; the director replays
    // it literally on restore. Falls back to the legacy explicitSetlist for old snapshots.
    explicitSetlist = resolvedSetlist ?: explicitSetlist,
    sourceName = sourceName,
    startingTrackId = currentTrackId,
    shuffled = shuffled,
    repeatMode = repeatMode,
    modified = modified,
)

/**
 * Capture the current session + active track + position + live play order as a persistable
 * [SessionSnapshot]. [setlist] is the director's resolved order at save time; an empty list is
 * stored as null (nothing to replay).
 */
internal fun snapshotOf(session: Session, track: Track?, positionMs: Long, setlist: List<Long>): SessionSnapshot =
    SessionSnapshot(
        type = session.type,
        contentId = session.contentId,
        explicitSetlist = session.explicitSetlist,
        sourceName = session.sourceName,
        currentTrackId = track?.id,
        shuffled = session.shuffled,
        repeatMode = session.repeatMode,
        positionMs = positionMs,
        resolvedSetlist = setlist.takeIf { it.isNotEmpty() },
        modified = session.modified,
    )
