package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.runtime.staticCompositionLocalOf
import net.sigmabeta.chipbox.capabilities.AppCapabilities
import net.sigmabeta.chipbox.features.componentlibrary.ComponentLibrary
import net.sigmabeta.chipbox.features.componentlibrary.ComponentLibraryMode
import net.sigmabeta.chipbox.features.crashlog.CrashLog
import net.sigmabeta.chipbox.features.errorlog.ErrorLog
import net.sigmabeta.chipbox.features.folderpicker.FolderPicker
import net.sigmabeta.chipbox.features.managelibrary.ManageLibrary
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatus
import net.sigmabeta.chipbox.features.rescanstatus.RescanStatus

/**
 * The host's capability policy, provided by [ChipboxAppUi]. Defaults to the full desktop/Android
 * surface so previews and hosts that do not pass one keep every destination reachable.
 */
internal val LocalAppCapabilities = staticCompositionLocalOf { AppCapabilities.Default }

/**
 * Whether [destination] may be pushed on this host. Screens already hide the rows that lead here;
 * this is the enforcement point for stale events, synthetic navigation, and deep links.
 *
 * The Error Log is deliberate: it is browser-/session-local, so it stays reachable when the debug
 * section is hidden (as a Diagnostics row) and is only blocked when the host withholds it too.
 */
internal fun AppCapabilities.allowsNavigation(destination: Any): Boolean = when (destination) {
    ManageLibrary, FolderPicker, RescanStatus -> canManageLibrary
    ErrorLog -> showErrorLog
    PlaybackStatus, CrashLog, ComponentLibrary -> showDebugSection
    is ComponentLibraryMode -> showDebugSection
    else -> true
}

/**
 * Shown when a screen emits `PickFolder` on a host that does not manage its own library. Kept as a
 * literal: the shell has no synchronous string provider, and this is a last-resort routing guard,
 * not normal UI copy (the web empty state carries the localized explanation).
 */
internal const val SERVER_MANAGED_LIBRARY_MESSAGE =
    "This library is managed on the server. Add music files to the library folder and restart " +
        "chipbox-server."
