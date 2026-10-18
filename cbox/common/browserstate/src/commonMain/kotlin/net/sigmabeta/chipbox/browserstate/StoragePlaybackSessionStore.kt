package net.sigmabeta.chipbox.browserstate

import net.sigmabeta.chipbox.player.persistence.PlaybackSessionStore
import net.sigmabeta.chipbox.player.persistence.SessionSnapshot
import net.sigmabeta.sage.storage.common.Storage

/**
 * [PlaybackSessionStore] over browser `localStorage` (through [VersionedStorageStore]).
 *
 * The shared [net.sigmabeta.chipbox.player.persistence.real.RealPlaybackSessionStore] writes a
 * bare JSON snapshot into DataStore. The web store keeps the same key but wraps it in ticket 06's
 * versioned envelope (with the real store's raw snapshot readable as schema version 0), because the
 * browser owns a shared, versioned state layer and every write must stay non-fatal on quota errors.
 *
 * [save] and [clear] are synchronous and never throw — the persister's `snapshotNow()` runs from a
 * `pagehide`/`visibilitychange` handler, where an uncaught quota `DOMException` would be lost or
 * could interrupt teardown. Failures surface as a non-fatal [BrowserStateEvent].
 *
 * Both are **explicit writes**, and unlike [VersionedStorageStore.update] they do not compare
 * against the previous blob: a new snapshot or an explicit stop must be persisted even when the
 * stored value is unreadable. That includes a blob written by a *newer* build
 * ([DecodeFailure.UNSUPPORTED_VERSION]): an explicit `save`/`clear` from this build replaces it.
 * The exception is deliberate rather than an oversight — this build cannot decode that blob (it
 * reads as "no session"), the [PlaybackSessionStore] contract has no way to report a refused
 * write, and a guard would need a *synchronous* read, which [Storage] does not offer (its reads
 * are suspending `Flow`s). A newer schema is therefore protected from implicit rewrites
 * (`update {}`) but not from an explicit save/clear.
 */
class StoragePlaybackSessionStore(
    private val store: VersionedStorageStore<PlaybackSessionState>,
) : PlaybackSessionStore {

    constructor(
        storage: Storage,
        events: BrowserStateEvents,
    ) : this(
        storage.versionedStore(
            key = BrowserStateKeys.PLAYBACK_SESSION,
            codec = BrowserStateCodecs.playbackSession,
            emptyValue = PlaybackSessionState(),
            events = events,
        ),
    )

    override suspend fun load(): SessionSnapshot? = store.current().snapshot

    override fun save(snapshot: SessionSnapshot) {
        store.write(PlaybackSessionState(snapshot))
    }

    override fun clear() {
        store.write(PlaybackSessionState())
    }
}
