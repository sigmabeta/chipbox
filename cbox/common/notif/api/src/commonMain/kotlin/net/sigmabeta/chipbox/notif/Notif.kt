package net.sigmabeta.chipbox.notif

import kotlinx.serialization.Serializable
import net.sigmabeta.chipbox.strings.api.ChipboxStringId

/**
 * One in-app notification row, shown on Home until dismissed (or, for [isOneTime] notices, until
 * the next app launch). [title] is a string id so the text is localized when the row renders;
 * [description] is already-resolved prose because its arguments (a file path, a failure message)
 * are only known where the notification is created.
 */
@Serializable
data class Notif(
    val id: Long,
    val title: ChipboxStringId,
    val description: String,
    val category: NotifCategory,
    val isOneTime: Boolean = false,
)
