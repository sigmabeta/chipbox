package net.sigmabeta.chipbox.notif

import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the active in-app notifications. Producers call [addNotif]; the Home notification module
 * observes [notifState] and the clear button routes back to [removeNotif].
 */
interface NotifManager {
    val notifState: StateFlow<NotifState>

    /** Add (or replace, when the id already exists) a notification. */
    fun addNotif(notif: Notif)

    /** Remove the notification with this id. Safe when it doesn't exist. */
    fun removeNotif(id: Long)
}
