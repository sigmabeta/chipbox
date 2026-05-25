package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * JVM/desktop actual — watches the pointer stream for a rising edge on the mouse "back" button.
 * Button state is only read (never consumed), so primary/secondary clicks are untouched; the
 * rising-edge guard fires [onBack] once per physical press instead of repeating while held.
 */
internal actual fun Modifier.backMouseButton(onBack: () -> Unit): Modifier = pointerInput(onBack) {
    awaitPointerEventScope {
        var backHeld = false
        while (true) {
            val backPressed = awaitPointerEvent().buttons.isBackPressed
            if (backPressed && !backHeld) {
                onBack()
            }
            backHeld = backPressed
        }
    }
}
