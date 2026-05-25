package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.ui.Modifier

/**
 * Android passthrough — see the commonMain declaration. The platform turns mouse back/forward
 * buttons into `KEYCODE_BACK` before they reach the view tree, and the shell already handles
 * that through Voyager's `onBackPressed`, so handling the pointer event here too would pop twice.
 */
internal actual fun Modifier.backMouseButton(onBack: () -> Unit): Modifier = this
