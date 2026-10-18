package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.runtime.staticCompositionLocalOf
import net.sigmabeta.chipbox.capabilities.AppCapabilities

/**
 * The host's capability policy, provided by [ChipboxAppUi]. Defaults to the full desktop/Android
 * surface so previews and hosts that do not pass one keep every destination reachable.
 */
internal val LocalAppCapabilities = staticCompositionLocalOf { AppCapabilities.Default }

/**
 * Shown when a screen emits `PickFolder` on a host that does not manage its own library. Kept as a
 * literal: the shell has no synchronous string provider, and this is a last-resort routing guard,
 * not normal UI copy (the web empty state carries the localized explanation).
 */
internal const val SERVER_MANAGED_LIBRARY_MESSAGE =
    "This library is managed on the server. Add music files to the library folder and restart " +
        "chipbox-server."
