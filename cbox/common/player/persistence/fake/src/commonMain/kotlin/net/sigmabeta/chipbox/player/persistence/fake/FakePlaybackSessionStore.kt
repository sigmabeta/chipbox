package net.sigmabeta.chipbox.player.persistence.fake

import net.sigmabeta.chipbox.player.persistence.PlaybackSessionStore
import net.sigmabeta.chipbox.player.persistence.SessionSnapshot

/**
 * In-memory [PlaybackSessionStore] for tests. Holds a single snapshot and records every write so
 * assertions can verify what (and how often) the persister stored or cleared.
 */
class FakePlaybackSessionStore(
    initial: SessionSnapshot? = null,
) : PlaybackSessionStore {
    var stored: SessionSnapshot? = initial
        private set

    val saveCalls: MutableList<SessionSnapshot> = mutableListOf()
    var clearCalls: Int = 0
        private set

    override suspend fun load(): SessionSnapshot? = stored

    override fun save(snapshot: SessionSnapshot) {
        saveCalls += snapshot
        stored = snapshot
    }

    override fun clear() {
        clearCalls++
        stored = null
    }
}
