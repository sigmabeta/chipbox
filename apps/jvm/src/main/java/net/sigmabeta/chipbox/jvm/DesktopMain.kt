package net.sigmabeta.chipbox.jvm

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import net.sigmabeta.chipbox.common.appui.api.ChipboxAppUi
import net.sigmabeta.chipbox.jvm.di.JvmChipboxGraph
import net.sigmabeta.chipbox.strings.api.LocalChipboxStringProvider
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

/**
 * Compose Multiplatform entry point for the JVM/desktop target. Calls the same
 * [ChipboxAppUi] composable the Android `MainActivity` does so the chrome / tab navigator /
 * per-tab Navigators are identical across both platforms — the dividend of converting
 * `cbox/android/appui/api` to `sage.kmp` and lifting the system event sink's Intent /
 * Clipboard usage into platform callbacks.
 *
 * `onOpenUrl` is `Desktop.browse(URI(url))` (the cross-platform AWT route — `xdg-open`
 * on Linux, `open` on macOS, `cmd /c start` on Windows). `onCopyToClipboard` writes to
 * the system clipboard via AWT. The folder-picker callback isn't wired here — it's the
 * `expect`/`actual` `SettingsRoute`'s job, and the JVM actual lives in
 * `features/settings/real/src/jvmMain` (uses `javax.swing.JFileChooser`).
 */
fun runDesktop(graph: JvmChipboxGraph) = application {
    // Window-level key handling so Escape/Backspace navigate back whenever the window is focused,
    // not only after a focusable child is clicked. `onKeyEvent` is the bubble-phase handler — it
    // sees only keys the focused composable didn't consume, so the Search text field keeps
    // Backspace for editing. `heldBackKeys` gives a rising-edge guard so auto-repeat (a held key)
    // fires a single back per physical press. The shell collects this flow (see ChipboxAppUi's
    // backKeyEvents) and routes each signal to its back handler.
    val backKeyEvents = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val heldBackKeys = remember { mutableSetOf<Key>() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Chipbox",
        icon = painterResource("ic_launcher.webp"),
        onKeyEvent = { event ->
            val isBackKey = event.key == Key.Escape || event.key == Key.Backspace
            when {
                !isBackKey -> false

                event.type == KeyEventType.KeyDown -> {
                    if (heldBackKeys.add(event.key)) backKeyEvents.tryEmit(Unit)
                    true
                }

                event.type == KeyEventType.KeyUp -> {
                    heldBackKeys.remove(event.key)
                    true
                }

                else -> false
            }
        },
    ) {
        CompositionLocalProvider(
            LocalMetroViewModelFactory provides graph.metroViewModelFactory,
            LocalChipboxStringProvider provides graph.stringProvider,
        ) {
            ChipboxAppUi(
                onOpenUrl = { url -> openUrlIfSupported(url) },
                onCopyToClipboard = { _, text -> copyToClipboard(text) },
                backKeyEvents = backKeyEvents,
            )
        }
    }
}

private fun openUrlIfSupported(url: String) {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (!desktop.isSupported(Desktop.Action.BROWSE)) return
    desktop.browse(URI(url))
}

private fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}
