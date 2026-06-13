package net.sigmabeta.chipbox.jvm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import java.awt.SplashScreen
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.common.appui.api.ChipboxAppUi
import net.sigmabeta.chipbox.jvm.di.JvmChipboxGraph
import net.sigmabeta.chipbox.strings.api.LocalChipboxStringProvider
import net.sigmabeta.sage.storage.common.Storage
import net.sigmabeta.sage.ui.perf.LocalLogger
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import kotlin.math.roundToInt

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
    // The desktop target is always a debug build (AppInfo.isDebug == true), but read it through the
    // graph rather than hardcoding so the title/icon track AppInfo if a release desktop ever ships —
    // matching the primary/secondary palette swap ChipboxAppUi applies on the same flag.
    val isDebug = graph.appInfo.isDebug
    val windowState = rememberPersistedWindowState(graph.storage)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = if (isDebug) "Chipbox Debug" else "Chipbox",
        icon = painterResource(if (isDebug) "ic_launcher_debug.webp" else "ic_launcher.webp"),
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
        // Dismiss the launcher splash (-splash:, shown since before main()) once the window's
        // content first composes — the real UI is up. getSplashScreen() is null when no splash
        // was launched (IDE run without the flag, headless), so this is a safe, idempotent no-op.
        LaunchedEffect(Unit) { SplashScreen.getSplashScreen()?.close() }
        CompositionLocalProvider(
            LocalMetroViewModelFactory provides graph.metroViewModelFactory,
            LocalChipboxStringProvider provides graph.stringProvider,
            LocalLogger provides graph.hatchet,
        ) {
            ChipboxAppUi(
                onOpenUrl = { url -> openUrlIfSupported(url) },
                onCopyToClipboard = { _, text -> copyToClipboard(text) },
                backKeyEvents = backKeyEvents,
            )
        }
    }
}

/**
 * A [WindowState] seeded with the size the window had when last closed (Compose defaults on first
 * launch) and kept persisted across launches via [Storage]. The stored flows are seeded
 * synchronously at construction, so the startup read returns at once; saves are debounced so a
 * single resize drag isn't a write storm.
 */
@OptIn(FlowPreview::class)
@Composable
private fun rememberPersistedWindowState(storage: Storage): WindowState {
    val windowState = rememberWindowState(
        size = remember {
            val width = runBlocking { storage.savedIntFlow(KEY_WINDOW_WIDTH).first() }
            val height = runBlocking { storage.savedIntFlow(KEY_WINDOW_HEIGHT).first() }
            if (width != null && height != null) {
                DpSize(width.dp, height.dp)
            } else {
                DpSize(DEFAULT_WINDOW_WIDTH.dp, DEFAULT_WINDOW_HEIGHT.dp)
            }
        },
    )

    LaunchedEffect(windowState, storage) {
        snapshotFlow { windowState.size }
            .filter { it.isSpecified }
            .debounce(WINDOW_SIZE_PERSIST_DEBOUNCE_MS)
            .collect { size ->
                storage.saveInt(KEY_WINDOW_WIDTH, size.width.value.roundToInt())
                storage.saveInt(KEY_WINDOW_HEIGHT, size.height.value.roundToInt())
            }
    }

    return windowState
}

private const val KEY_WINDOW_WIDTH = "window.width"
private const val KEY_WINDOW_HEIGHT = "window.height"

// Compose's own default window size — used on first launch, before anything is stored.
private const val DEFAULT_WINDOW_WIDTH = 800
private const val DEFAULT_WINDOW_HEIGHT = 600

private const val WINDOW_SIZE_PERSIST_DEBOUNCE_MS = 500L

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
