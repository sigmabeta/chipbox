@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package net.sigmabeta.chipbox.js

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.ComposeViewport
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.browser.document
import kotlinx.browser.window
import net.sigmabeta.chipbox.js.analytics.WebAnalytics
import net.sigmabeta.chipbox.js.image.buildWebImageLoader
import net.sigmabeta.chipbox.js.mediasession.WebMediaSession
import net.sigmabeta.chipbox.js.repository.RemoteRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.common.appui.api.ChipboxAppUi
import net.sigmabeta.chipbox.js.di.WebChipboxGraph
import net.sigmabeta.chipbox.js.emulators.WasmGbaEmulator
import net.sigmabeta.chipbox.js.emulators.WasmGmeEmulator
import net.sigmabeta.chipbox.js.emulators.WasmNcsfEmulator
import net.sigmabeta.chipbox.js.emulators.WasmPsfEmulator
import net.sigmabeta.chipbox.js.emulators.WasmSsfEmulator
import net.sigmabeta.chipbox.js.emulators.WasmTwosfEmulator
import net.sigmabeta.chipbox.js.emulators.WasmUsfEmulator
import net.sigmabeta.chipbox.js.emulators.WasmVgmEmulator
import net.sigmabeta.chipbox.js.emulators.WasmVgmstreamEmulator
import net.sigmabeta.chipbox.js.logging.WebHatchet
import net.sigmabeta.chipbox.js.wasm.loadChipboxGba
import net.sigmabeta.chipbox.js.wasm.loadChipboxGme
import net.sigmabeta.chipbox.js.wasm.loadChipboxNcsf
import net.sigmabeta.chipbox.js.wasm.loadChipboxPsf
import net.sigmabeta.chipbox.js.wasm.loadChipboxSsf
import net.sigmabeta.chipbox.js.wasm.loadChipboxTwosf
import net.sigmabeta.chipbox.js.wasm.loadChipboxUsf
import net.sigmabeta.chipbox.js.wasm.loadChipboxVgm
import net.sigmabeta.chipbox.js.wasm.loadChipboxVgmstream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.KeyboardEvent
import net.sigmabeta.chipbox.strings.api.LocalChipboxStringProvider
import net.sigmabeta.chipbox.strings.real.ChipboxStringProvider
import net.sigmabeta.chipbox.strings.real.loadChipboxStrings
import net.sigmabeta.sage.ui.perf.LocalLogger

/**
 * Browser entry point. Mirrors `apps/jvm`'s `runDesktop` and `apps/android`'s `MainActivity.onCreate`
 * — load i18n strings, build the Metro graph, mount [ChipboxAppUi] with platform callbacks.
 *
 * Strings are preloaded inside [MainScope.launch] because `loadChipboxStrings()` is suspend
 * (Compose's `getString` is suspend on the multiplatform side). The splash element in `index.html`
 * is removed right before [ComposeViewport] takes over so the user sees a transient "Loading…"
 * rather than a blank black page during the ~10-200ms preload.
 *
 * Platform callbacks:
 *  - `onOpenUrl` -> `window.open(url, "_blank")` (new tab).
 *  - `onCopyToClipboard` -> `navigator.clipboard.writeText(text)` (modern Clipboard API; requires
 *    a secure context, which `localhost` and `https://` both satisfy).
 *  - `backKeyEvents` -> Escape keypresses on `window`. Mirrors desktop's
 *    `Key.Escape`-only back affordance (no Backspace — the browser already uses Backspace for
 *    URL back-navigation when no text input is focused, and we don't want to intercept that).
 *    The actual browser back/forward buttons stay native — intercepting them via `popstate` /
 *    `pushState` would conflict with the URL bar and break the user's expected behavior.
 */
fun main() {
    val hatchet = WebHatchet()
    // Install Coil's image loader before any Composable touches `LocalAsyncImagePainter` /
    // `rememberAsyncImagePainter`. Without this, JS has no http(s) fetcher and a Mapper to
    // rewrite the scanner's host-side photoUrl paths into `/api/files/by-path` URLs, so every
    // `GridImage` renders its error state.
    val analytics = WebAnalytics(hatchet)
    // Reuse the same baseUrl logic RemoteRepository + HttpContentSource use — handles the
    // webpack-devserver-on-:8081 → API-on-:8080 swap so images load in dev too.
    val apiBaseUrl = RemoteRepository.resolveBaseUrl()
    SingletonImageLoader.setSafe { context: PlatformContext ->
        buildWebImageLoader(
            context = context,
            baseUrl = apiBaseUrl,
            hatchet = hatchet,
            analytics = analytics,
        )
    }
    MainScope().launch {
        // Each WasmXxxEmulator lazy-loads its WASM module on first use via
        // `Emulator.ensureNativeLibReady()` — the factory awaits it before the source is
        // constructed. Page-load no longer pulls down all ~4 MB of WASM up front.

        val stringProvider = ChipboxStringProvider(loadChipboxStrings())
        val graph = createGraphFactory<WebChipboxGraph.Factory>().create(
            hatchet = hatchet,
            stringProvider = stringProvider,
        )

        // The web target is always a debug build; mark the browser tab title to match the
        // primary/secondary palette swap ChipboxAppUi applies on the same AppInfo.isDebug flag.
        if (graph.appInfo.isDebug) {
            document.title = "Chipbox Debug"
        }

        // Hook the browser's media-session API into the player so OS-level transport (media keys,
        // lock-screen controls, Bluetooth headset buttons) drives the same Director.
        WebMediaSession(graph.director, apiBaseUrl).install(graph.appScope)

        // Back-key stream — fed by window-level Escape keypresses (Backspace is browser-back
        // navigation, leave it alone). Skip events whose target is a text input so the field
        // can still clear / lose focus naturally.
        val backKeyEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        window.addEventListener("keydown", { rawEvent ->
            val event = rawEvent.unsafeCast<KeyboardEvent>()
            if (event.key != "Escape") return@addEventListener
            val target = event.target
            if (target is HTMLInputElement || target is HTMLTextAreaElement) return@addEventListener
            backKeyEvents.tryEmit(Unit)
        })

        document.getElementById("splash")?.remove()

        ComposeViewport(document.body!!) {
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides graph.metroViewModelFactory,
                LocalChipboxStringProvider provides graph.stringProvider,
                LocalLogger provides graph.hatchet,
            ) {
                // Arrow keys -> directional focus movement, the way Android's D-pad does it natively.
                // Compose's web (ComposeViewport) target, like desktop, only wires Tab/Shift-Tab to
                // focus traversal, so without this focus never moves with the arrow keys. `onKeyEvent`
                // is the bubble-phase handler: the focused composable sees each key first (text fields
                // keep Left/Right for the caret, a focused list item ignores arrows), and only
                // unconsumed keys reach here. `moveFocus` returns whether focus actually moved — we
                // return that so an at-the-edge press isn't swallowed, and so LazyColumn's
                // beyond-bounds composition kicks in when stepping past the last visible row.
                val focusManager = LocalFocusManager.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val direction = when (event.key) {
                                Key.DirectionUp -> FocusDirection.Up
                                Key.DirectionDown -> FocusDirection.Down
                                Key.DirectionLeft -> FocusDirection.Left
                                Key.DirectionRight -> FocusDirection.Right
                                else -> return@onKeyEvent false
                            }
                            focusManager.moveFocus(direction)
                        },
                ) {
                    ChipboxAppUi(
                        onOpenUrl = { url -> window.open(url, "_blank") },
                        onCopyToClipboard = { _, text -> window.navigator.clipboard.writeText(text) },
                        backKeyEvents = backKeyEvents,
                    )
                }
            }
        }
    }
}
