package net.sigmabeta.chipbox.notif

import kotlinx.serialization.Serializable

/** The persisted set of active notifications, keyed by [Notif.id]. */
@Serializable
data class NotifState(
    val notifs: Map<Long, Notif> = emptyMap(),
)
