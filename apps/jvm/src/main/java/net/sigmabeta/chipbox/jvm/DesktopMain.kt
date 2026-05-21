package net.sigmabeta.chipbox.jvm

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import net.sigmabeta.chipbox.appui.api.ChipboxAppUi
import net.sigmabeta.chipbox.jvm.di.JvmChipboxGraph
import net.sigmabeta.chipbox.strings.api.LocalChipboxStringProvider

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
    Window(onCloseRequest = ::exitApplication, title = "Chipbox") {
        CompositionLocalProvider(
            LocalMetroViewModelFactory provides graph.metroViewModelFactory,
            LocalChipboxStringProvider provides graph.stringProvider,
        ) {
            ChipboxAppUi(
                onOpenUrl = { url -> openUrlIfSupported(url) },
                onCopyToClipboard = { _, text -> copyToClipboard(text) },
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
