package net.sigmabeta.chipbox.notif.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.sigmabeta.chipbox.notif.Notif
import net.sigmabeta.chipbox.notif.NotifManager
import net.sigmabeta.chipbox.notif.NotifState

/**
 * In-memory [NotifManager] for tests and the UI-test harness. Seed it through [addNotif] (or the
 * constructor) and assert removals directly; no storage or coroutine scope is involved.
 */
class FakeNotifManager(
    initialNotifs: List<Notif> = emptyList(),
) : NotifManager {

    private val mutableNotifState = MutableStateFlow(
        NotifState(initialNotifs.associateBy { it.id }),
    )

    override val notifState: StateFlow<NotifState> = mutableNotifState.asStateFlow()

    override fun addNotif(notif: Notif) {
        mutableNotifState.update { it.copy(notifs = it.notifs + (notif.id to notif)) }
    }

    override fun removeNotif(id: Long) {
        mutableNotifState.update { it.copy(notifs = it.notifs - id) }
    }
}
