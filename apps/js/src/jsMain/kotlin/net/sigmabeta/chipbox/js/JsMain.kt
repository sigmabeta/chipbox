@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package net.sigmabeta.chipbox.js

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeViewport
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import kotlinx.browser.document
import kotlinx.browser.window
import net.sigmabeta.chipbox.js.analytics.WebAnalytics
import net.sigmabeta.chipbox.js.image.buildWebImageLoader
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
        // Pre-load every WASM emulator module before constructing the graph: the chipbox
        // Emulator contract's `loadNativeLib()` is synchronous, so each WASM instance must
        // already be resolved by the time a Director starts a track and reaches
        // `Wasm<Name>Emulator.loadNativeLib`. Done in parallel — the modules are small
        // (libgme ~91 KB, libvgm ~few hundred KB) and independent.
        coroutineScope {
            val gme = async { loadChipboxGme() }
            val vgm = async { loadChipboxVgm() }
            val ssf = async { loadChipboxSsf() }
            val usf = async { loadChipboxUsf() }
            val psf = async { loadChipboxPsf() }
            val ncsf = async { loadChipboxNcsf() }
            val twosf = async { loadChipboxTwosf() }
            val gba = async { loadChipboxGba() }
            val vgmstream = async { loadChipboxVgmstream() }
            awaitAll(gme, vgm, ssf, usf, psf, ncsf, twosf, gba, vgmstream)
            WasmGmeEmulator.setLoadedModule(gme.getCompleted())
            WasmVgmEmulator.setLoadedModule(vgm.getCompleted())
            WasmSsfEmulator.setLoadedModule(ssf.getCompleted())
            WasmUsfEmulator.setLoadedModule(usf.getCompleted())
            WasmPsfEmulator.setLoadedModule(psf.getCompleted())
            WasmNcsfEmulator.setLoadedModule(ncsf.getCompleted())
            WasmTwosfEmulator.setLoadedModule(twosf.getCompleted())
            WasmGbaEmulator.setLoadedModule(gba.getCompleted())
            WasmVgmstreamEmulator.setLoadedModule(vgmstream.getCompleted())
        }

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
