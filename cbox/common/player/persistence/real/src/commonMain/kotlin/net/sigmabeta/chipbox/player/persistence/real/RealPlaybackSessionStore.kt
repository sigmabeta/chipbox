package net.sigmabeta.chipbox.player.persistence.real

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.player.persistence.PlaybackSessionStore
import net.sigmabeta.chipbox.player.persistence.SessionSnapshot
import net.sigmabeta.sage.storage.common.Storage

/**
 * [PlaybackSessionStore] backed by the shared [Storage] (DataStore) abstraction, mirroring the
 * settings managers: the snapshot is JSON-encoded into a single string key. [Storage] has no
 * "remove", so [clear] writes an empty string that [load] reads back as "nothing saved".
 */
class RealPlaybackSessionStore(
    private val storage: Storage,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : PlaybackSessionStore {
    override suspend fun load(): SessionSnapshot? {
        val raw = storage.savedStringFlow(KEY_SNAPSHOT).first()
        if (raw.isNullOrEmpty()) return null
        // A snapshot from an older schema (or a corrupt write) decodes to null rather than
        // throwing out of launch; the caller treats that as "nothing to restore".
        return runCatching { json.decodeFromString(SessionSnapshot.serializer(), raw) }.getOrNull()
    }

    override fun save(snapshot: SessionSnapshot) {
        storage.saveString(KEY_SNAPSHOT, json.encodeToString(SessionSnapshot.serializer(), snapshot))
    }

    override fun clear() {
        storage.saveString(KEY_SNAPSHOT, "")
    }

    companion object {
        const val KEY_SNAPSHOT = "playback.session.snapshot"
    }
}
