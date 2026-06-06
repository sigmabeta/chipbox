package net.sigmabeta.chipbox.player.persistence.real

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.persistence.PlaybackSessionPersister
import net.sigmabeta.chipbox.player.persistence.PlaybackSessionStore
import net.sigmabeta.sage.logging.Hatchet

/**
 * Production [PlaybackSessionPersister].
 *
 * ### Restore
 * [restore] reads the stored snapshot and hands it to [Director.restore], which loads the track
 * paused at the saved position. Any failure (missing snapshot, a track since deleted, a stale id
 * after a library rescan) is swallowed and the snapshot cleared, so a bad save can never wedge
 * launch.
 *
 * ### Save
 * [observe] watches the merged session/playback/metadata streams and writes the snapshot on the
 * `PLAYING -> PAUSED` edge — the one moment a position worth resuming exists, with no throttling.
 * Keying on that specific transition (rather than "entered PAUSED") is deliberate: a restored
 * session lands in PAUSED *from BUFFERING*, so its own restore can't immediately re-save a
 * zero position over the good one. Reaching [PlayerState.STOPPED] (explicit stop, or a setlist
 * playing through to the end) clears the snapshot — there's nothing left to resume.
 *
 * [snapshotNow] is the teardown net for "closed while playing": it writes whatever's current
 * regardless of state, so a session never paused before the process died still comes back.
 */
class RealPlaybackSessionPersister(
    private val director: Director,
    private val store: PlaybackSessionStore,
    private val hatchet: Hatchet,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PlaybackSessionPersister {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var observing = false

    /** Latest combined playback slice seen by [observe], cached so [snapshotNow] can write without
     *  suspending. `@Volatile` because it's read from whatever thread calls [snapshotNow]. */
    @Volatile
    private var latest: Snapshotable? = null

    /** Set by [snapshotNow] (the teardown net). Tearing the service down releases the player, which
     *  emits [PlayerState.STOPPED]; the observer would read that as an explicit stop and `clear()`
     *  the snapshot [snapshotNow] just wrote — losing the session we meant to resume. While this is
     *  true the observer treats a STOPPED as a teardown artifact and does NOT clear. It resets when
     *  genuine playback resumes (a PLAYING tick) or on the next [restore], so a real user-stop or a
     *  setlist playing to its end still clears as before. `@Volatile`: written from the teardown
     *  thread, read by the collector. */
    @Volatile
    private var tearingDown = false

    /** Stop observing and cancel the scope. Unused in production (the singleton lives for the
     *  process); here so tests can tear the collector down. */
    fun release() {
        scope.cancel()
    }

    override suspend fun restore() {
        // A fresh restore means we're live again — any teardown suppression from a previous service
        // lifetime (the persister is a process singleton) no longer applies.
        tearingDown = false
        val snapshot = runCatching { store.load() }
            .onFailure { hatchet.w("Failed to load saved session: $it") }
            .getOrNull()
            ?: return

        try {
            hatchet.i("Restoring saved session (track=${snapshot.currentTrackId}, pos=${snapshot.positionMs}ms).")
            director.restore(snapshot.toSession(), snapshot.positionMs)
        } catch (t: Throwable) {
            hatchet.w("Failed to restore saved session, clearing it: $t")
            store.clear()
        }
    }

    override fun observe() {
        if (observing) return
        observing = true

        scope.launch {
            var previousState: PlayerState? = null
            combine(
                director.sessionState(),
                director.playbackState(),
                director.metadataState(),
            ) { session, playback, track -> Snapshotable(session, playback.state, playback.position, track) }
                .collect { current ->
                    latest = current
                    // Genuine playback resumed — cancel any teardown suppression so a later stop
                    // clears normally (e.g. a backgrounded session that plays its setlist to the end).
                    if (current.state == PlayerState.PLAYING) tearingDown = false
                    when {
                        // Don't clear on a STOPPED that's just the player being released during
                        // teardown — that would wipe the snapshot snapshotNow() wrote. A real
                        // stop arrives with tearingDown == false (reset on the preceding PLAYING).
                        current.state == PlayerState.STOPPED -> if (!tearingDown) store.clear()

                        previousState == PlayerState.PLAYING && current.state == PlayerState.PAUSED ->
                            current.session?.let {
                                store.save(snapshotOf(it, current.track, current.positionMs))
                            }
                    }
                    previousState = current.state
                }
        }
    }

    override fun snapshotNow() {
        val current = latest ?: return
        val session = current.session ?: return
        if (current.state == PlayerState.STOPPED || current.state == PlayerState.IDLE) return
        // Mark teardown BEFORE the save so the STOPPED the collector sees when the player is
        // released next can't race ahead and clear what we're about to write.
        tearingDown = true
        store.save(snapshotOf(session, current.track, current.positionMs))
    }

    /** The slice of the three observed streams a snapshot is built from, combined into one value. */
    private data class Snapshotable(
        val session: Session?,
        val state: PlayerState,
        val positionMs: Long,
        val track: Track?,
    )
}
