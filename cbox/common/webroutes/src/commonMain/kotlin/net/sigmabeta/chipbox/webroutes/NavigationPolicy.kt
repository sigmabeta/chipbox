package net.sigmabeta.chipbox.webroutes

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
 * Whether [destination] may be pushed on this host.
 *
 * This is the single capability policy shared by the in-app event sinks and the URL codec: a route
 * the shell refuses must not become a deep link, and vice versa. It lives in this pure module (not
 * in the Compose shell) so the web URL mapping can apply it under Node tests.
 *
 * The Error Log is deliberate: it is browser-/session-local, so it stays reachable when the debug
 * section is hidden (as a Diagnostics row) and is only blocked when the host withholds it too.
 * Admin and debug-only destinations have no URL at all; the decoder never produces them, and this
 * guard is the second line of defence for any destination a caller builds directly.
 */
fun AppCapabilities.allowsNavigation(destination: Any): Boolean = when (destination) {
    ManageLibrary, FolderPicker, RescanStatus -> canManageLibrary
    ErrorLog -> showErrorLog
    PlaybackStatus, CrashLog, ComponentLibrary -> showDebugSection
    is ComponentLibraryMode -> showDebugSection
    else -> true
}
