package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.runtime.staticCompositionLocalOf
import net.sigmabeta.chipbox.capabilities.AppCapabilities

/**
 * The host's capability policy, provided by [ChipboxAppUi]. Defaults to the full desktop/Android
 * surface so previews and hosts that do not pass one keep every destination reachable.
 */
internal val LocalAppCapabilities = staticCompositionLocalOf { AppCapabilities.Default }
