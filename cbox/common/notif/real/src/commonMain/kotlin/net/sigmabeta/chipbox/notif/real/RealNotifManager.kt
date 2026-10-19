package net.sigmabeta.chipbox.notif.real

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.notif.Notif
import net.sigmabeta.chipbox.notif.NotifManager
import net.sigmabeta.chipbox.notif.NotifState
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.storage.common.Storage

/**
 * [NotifManager] that persists the active notifications in SAGE [Storage].
 *
 * The persisted state is read once on construction so one-time notifications left over from a
 * previous launch can be dropped. From then on the in-memory state is authoritative: every
 * add/remove updates it immediately and writes through, so a notification added during the
 * startup read can't be clobbered by the older stored snapshot.
 */
class RealNotifManager(
    private val storage: Storage,
    scope: CoroutineScope,
    private val hatchet: Hatchet,
) : NotifManager {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutableNotifState = MutableStateFlow(NotifState())
    override val notifState: StateFlow<NotifState> = mutableNotifState.asStateFlow()

    init {
        scope.launch {
            val loaded = storage.savedStringFlow(KEY_NOTIF_STATE).first().toNotifState()
            // Drop one-time notices from the previous launch, but merge anything that was added
            // while the read was in flight.
            val cleaned = loaded.copy(notifs = loaded.notifs.filterNot { it.value.isOneTime })
            mutableNotifState.update { current ->
                cleaned.copy(notifs = cleaned.notifs + current.notifs)
            }
            if (cleaned != loaded) save(mutableNotifState.value)
        }
    }

    override fun addNotif(notif: Notif) {
        mutableNotifState.update { it.copy(notifs = it.notifs + (notif.id to notif)) }
        save(mutableNotifState.value)
    }

    override fun removeNotif(id: Long) {
        mutableNotifState.update { it.copy(notifs = it.notifs - id) }
        save(mutableNotifState.value)
    }

    private fun save(state: NotifState) {
        storage.saveString(KEY_NOTIF_STATE, json.encodeToString(state))
    }

    private fun String?.toNotifState(): NotifState {
        if (this == null) return NotifState()
        return try {
            json.decodeFromString<NotifState>(this)
        } catch (ex: Exception) {
            hatchet.e("Error reading notifs from storage: ${ex.message}")
            NotifState()
        }
    }

    companion object {
        const val KEY_NOTIF_STATE = "notif.state"
    }
}
