package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.ui.Modifier

/**
 * Routes a press of the mouse "back" side-button to [onBack].
 *
 * JVM/desktop only: Compose desktop surfaces the back button through
 * `PointerButtons.isBackPressed`, so the JVM actual watches the pointer stream. On Android the
 * platform intercepts mouse back/forward buttons in `ViewRootImpl` and re-dispatches them as
 * `KEYCODE_BACK`, which already reaches the shell via Voyager's `onBackPressed` (`DeviceBack`) —
 * so the Android actual is a passthrough to avoid popping twice for one click.
 */
internal expect fun Modifier.backMouseButton(onBack: () -> Unit): Modifier
