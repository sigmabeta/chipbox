@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package net.sigmabeta.chipbox.js

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeViewport
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.common.appui.api.ChipboxAppUi
import net.sigmabeta.chipbox.js.di.WebChipboxGraph
import net.sigmabeta.chipbox.js.emulators.WasmGmeEmulator
import net.sigmabeta.chipbox.js.logging.WebHatchet
import net.sigmabeta.chipbox.js.wasm.loadChipboxGme
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
 *  - `backKeyEvents` -> `null`; there's no window-level back-key concept in the browser. (Browser
 *    back/forward maps to history navigation, not in-app navigation — wiring it would conflict
 *    with the host page's URL bar.)
 */
fun main() {
    val hatchet = WebHatchet()
    MainScope().launch {
        // Pre-load the libgme WASM module before constructing the graph: the chipbox Emulator
        // contract's `loadNativeLib()` is synchronous, so the WASM instance must already be
        // resolved by the time a Director starts a track and reaches WasmGmeEmulator.loadNativeLib.
        WasmGmeEmulator.setLoadedModule(loadChipboxGme())

        val stringProvider = ChipboxStringProvider(loadChipboxStrings())
        val graph = createGraphFactory<WebChipboxGraph.Factory>().create(
            hatchet = hatchet,
            stringProvider = stringProvider,
        )

        document.getElementById("splash")?.remove()

        ComposeViewport(document.body!!) {
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides graph.metroViewModelFactory,
                LocalChipboxStringProvider provides graph.stringProvider,
                LocalLogger provides graph.hatchet,
            ) {
                ChipboxAppUi(
                    onOpenUrl = { url -> window.open(url, "_blank") },
                    onCopyToClipboard = { _, text -> window.navigator.clipboard.writeText(text) },
                    backKeyEvents = null,
                )
            }
        }
    }
}
