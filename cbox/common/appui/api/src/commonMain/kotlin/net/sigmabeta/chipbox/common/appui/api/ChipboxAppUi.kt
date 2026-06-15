package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.chrome.api.ChromeController
import net.sigmabeta.chipbox.common.ui.chrome.api.LocalChipboxEventSink
import net.sigmabeta.chipbox.common.ui.chrome.api.LocalChromeController
import net.sigmabeta.chipbox.common.ui.chrome.api.LocalTitleBarController
import net.sigmabeta.chipbox.common.ui.chrome.api.TitleBarController
import net.sigmabeta.chipbox.common.ui.components.api.subs.LocalForceFakeImages
import net.sigmabeta.chipbox.settings.ThemeMode
import net.sigmabeta.chipbox.ui.theme.api.AppTheme

/**
 * Cross-platform entry point for the Chipbox Compose UI. Owns the outer Voyager [Navigator]
 * whose root is [ChipboxTabsScreen] (chrome + per-tab navigators). `PlayerStatus.onClick`
 * pushes `NowPlayingScreen` onto the *active tab's* inner Navigator so it renders inside the
 * scaffold content, leaving `ChromeController` to hide the top bar + PlayerStatus (and keep
 * the nav bar) — matching the pre-Voyager shell.
 *
 * Platform-specific event handling — opening URLs and copying to the clipboard — comes in
 * as callbacks from each host app. Android `MainActivity` passes
 * `Intent(ACTION_VIEW, …) + startActivity` for [onOpenUrl] and
 * `ClipboardManager.setPrimaryClip` for [onCopyToClipboard]; JVM `DesktopMain.kt` passes
 * `Desktop.browse(URI(url))` and `Toolkit.getDefaultToolkit().systemClipboard.setContents
 * (StringSelection(text), null)`. Keeping the chrome itself platform-free is what lets this
 * composable live in commonMain.
 *
 * [LocalChipboxEventSink] + [LocalAppSnackbarHostState] are provided *inside* the outer
 * Navigator block but *outside* `SlideTransition` so they stay in scope for the tabs root
 * (the outer sink references this navigator). Providing them any deeper would unmount the
 * locals during navigation.
 */
@Suppress("LongMethod")
@Composable
fun ChipboxAppUi(
    onOpenUrl: (String) -> Unit,
    onCopyToClipboard: (label: String, text: String) -> Unit,
    modifier: Modifier = Modifier,
    // Platform window/activity-level back keys (Escape/Backspace). Routed to the shell's back
    // handler in [ChipboxTabsScreen] so they fire whenever the window is focused — see
    // [LocalPlatformBackKeys]. Defaults to null for hosts (and previews) that don't wire keys.
    backKeyEvents: Flow<Unit>? = null,
    // Route-key destinations to open inside the active tab (where the chrome lives), driven from
    // outside a screen's composition — a UI test starting at a deep screen, or a future deep-link.
    // Routed in [ChipboxTabsScreen]; see [LocalActiveTabDestinations]. Null for hosts that don't
    // wire programmatic navigation (the apps today).
    activeTabDestinations: Flow<Any>? = null,
    // Observer called with each destination the shell navigates to (active-tab deep pushes). Lets a
    // UI test assert that an interaction triggered the expected navigation. See
    // [LocalNavigationObserver]. Null for hosts that aren't observing (the apps today).
    onNavigate: ((Any) -> Unit)? = null,
) {
    // Materialized before the theme so its persisted theme choice picks the color scheme — and
    // so Metro multibinding misses surface at launch rather than at the first screen entry.
    val appUiViewModel = metroViewModel<ChipboxAppUiViewModel>()
    val themeMode by appUiViewModel.themeMode.collectAsState()
    val brand by appUiViewModel.brandFont.collectAsState()
    val plain by appUiViewModel.plainFont.collectAsState()
    val forceFakeImages by appUiViewModel.forceFakeImages.collectAsState()

    AppTheme(
        brand = brand,
        plain = plain,
        darkTheme = when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> null
        },
        // Debug builds swap primary/secondary so they're unmistakable next to a release build.
        swapPrimaryAndSecondary = appUiViewModel.isDebugBuild,
    ) {
        val titleBarController = remember { TitleBarController() }
        val chromeController = remember { ChromeController() }
        val snackbarHostState = remember { SnackbarHostState() }
        val snackbarScope = rememberCoroutineScope()

        CompositionLocalProvider(
            LocalTitleBarController provides titleBarController,
            LocalChromeController provides chromeController,
            LocalAppSnackbarHostState provides snackbarHostState,
            LocalPlatformBackKeys provides backKeyEvents,
            LocalActiveTabDestinations provides activeTabDestinations,
            LocalNavigationObserver provides onNavigate,
            // Debug "image loader = FAKE" → render generated gradient art instead of fetching.
            LocalForceFakeImages provides forceFakeImages,
            // Expose the singleton VM so the shell (which lives inside Voyager and resolves
            // its own ViewModelStoreOwner) can reach the same instance rather than getting
            // a fresh one from `metroViewModel<…>()`.
            LocalChipboxAppUiViewModel provides appUiViewModel,
        ) {
            Navigator(ChipboxTabsScreen) { navigator ->
                // Single dispatcher for "do the side effect" — same shape as before, but
                // now invoked by a collector on the VM's [effects] flow rather than directly
                // by the LocalChipboxEventSink. Policy decisions (what to honour, what to
                // drop) live in the VM; this layer just executes whatever the VM forwards.
                val applyEffect = remember(
                    navigator,
                    snackbarHostState,
                    snackbarScope,
                    onOpenUrl,
                    onCopyToClipboard,
                    chromeController,
                ) {
                    buildOuterSink(
                        snackbarHostState = snackbarHostState,
                        snackbarScope = snackbarScope,
                        onNavigateTo = { destination ->
                            navigator.push(screenFor(destination))
                        },
                        onNavigateBack = { navigator.pop() },
                        onOpenUrl = onOpenUrl,
                        onCopyToClipboard = onCopyToClipboard,
                        onRequestMiniPlayerVisibility = { visible ->
                            chromeController.set(
                                chromeController.state.copy(showPlayerStatus = visible),
                            )
                        },
                        onRequestTopBarVisibility = { visible ->
                            chromeController.set(
                                chromeController.state.copy(showTopBar = visible),
                            )
                        },
                    )
                }

                // Drain the VM's effect stream into the dispatcher above. Collect runs on
                // the main dispatcher (LaunchedEffect's default), which is what Voyager /
                // ChromeController / SnackbarHostState all expect.
                LaunchedEffect(appUiViewModel, applyEffect) {
                    appUiViewModel.effects.collect(applyEffect)
                }

                // Screens emit events into this sink — which is just a method call on the
                // VM. The VM decides whether to forward (or alter) each event by re-emitting
                // it on [effects].
                val outerSink: (ChipboxEvent) -> Unit =
                    remember(appUiViewModel) { appUiViewModel::handleEvent }
                CompositionLocalProvider(LocalChipboxEventSink provides outerSink) {
                    SlideTransition(navigator, modifier = modifier)
                }
            }
        }
    }
}
