package net.sigmabeta.chipbox.notif

import kotlinx.serialization.Serializable

/** Drives the row's styling: [ERROR] notifications render with the error container colors. */
@Serializable
enum class NotifCategory {
    ERROR,
    INFO,
}
