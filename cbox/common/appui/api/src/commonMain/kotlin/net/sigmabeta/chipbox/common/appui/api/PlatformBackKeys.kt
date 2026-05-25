package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow

/**
 * A stream of "the user pressed a back key" signals (Escape / Backspace) originating from the host
 * platform's window/activity-level key handling — desktop `Window.onKeyEvent`, Android
 * `Activity.onKeyDown` — provided by [ChipboxAppUi] from its `backKeyEvents` parameter.
 *
 * Window/activity-level handling is used instead of an in-composition `onKeyEvent` so these keys
 * work whenever the window is focused, not only after a focusable child has been clicked. Both
 * entry points fire only for keys the focused content didn't consume, so a focused text field still
 * gets Backspace for editing. [ChipboxTabsScreen] collects this and routes each signal to the
 * shared back handler as `DeviceBack`. `null` (the default) means no platform key stream is wired
 * (e.g. screenshot/host-less previews).
 */
internal val LocalPlatformBackKeys = staticCompositionLocalOf<Flow<Unit>?> { null }
