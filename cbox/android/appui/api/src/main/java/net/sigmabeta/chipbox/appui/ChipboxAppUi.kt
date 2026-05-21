package net.sigmabeta.chipbox.appui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.ui.chrome.ChromeController
import net.sigmabeta.chipbox.ui.chrome.LocalChipboxEventSink
import net.sigmabeta.chipbox.ui.chrome.LocalChromeController
import net.sigmabeta.chipbox.ui.chrome.LocalTitleBarController
import net.sigmabeta.chipbox.ui.chrome.TitleBarController
import net.sigmabeta.chipbox.ui.theme.AppTheme
import net.sigmabeta.sage.ui.StringProvider

/**
 * Entry point for the Android Compose UI. Owns the outer Voyager [Navigator] whose root is
 * [ChipboxTabsScreen] (chrome + per-tab navigators); pushing onto this navigator covers the
 * whole tab UI — used by `PlayerStatus.onClick` to surface `NowPlayingScreen` as a true
 * full-screen overlay rather than relying on `ChromeController` to hide bars.
 *
 * [LocalChipboxEventSink] + [LocalAppSnackbarHostState] are provided *inside* the outer
 * Navigator block but *outside* `SlideTransition` so they remain in scope whether the
 * tabs root or a pushed screen (e.g. NowPlaying) is currently rendered. Providing them
 * any deeper would unmount the locals along with the tab UI when NowPlaying is on top.
 *
 * `stringProvider` is unused here today — every screen pulls `LocalChipboxStringProvider`
 * (provided in `MainActivity`) — but the parameter stays for API parity with callers that
 * still hand it in.
 */
@Composable
@Suppress("UnusedParameter")
fun ChipboxAppUi(stringProvider: StringProvider, modifier: Modifier = Modifier) {
    AppTheme {
        // Eagerly materialize the appui-scoped VM so Metro multibinding misses surface at
        // launch rather than at the first screen entry (matches the pre-Voyager smoke).
        metroViewModel<ChipboxAppUiViewModel>()

        val titleBarController = remember { TitleBarController() }
        val chromeController = remember { ChromeController() }
        val snackbarHostState = remember { SnackbarHostState() }
        val snackbarScope = rememberCoroutineScope()
        val context = LocalContext.current

        CompositionLocalProvider(
            LocalTitleBarController provides titleBarController,
            LocalChromeController provides chromeController,
            LocalAppSnackbarHostState provides snackbarHostState,
        ) {
            Navigator(ChipboxTabsScreen) { navigator ->
                val outerSink = remember(navigator, snackbarHostState, snackbarScope, context) {
                    buildOuterSink(
                        context = context,
                        snackbarHostState = snackbarHostState,
                        snackbarScope = snackbarScope,
                        onNavigateTo = { destination ->
                            navigator.push(screenFor(destination))
                        },
                        onNavigateBack = { navigator.pop() },
                    )
                }
                CompositionLocalProvider(LocalChipboxEventSink provides outerSink) {
                    SlideTransition(navigator, modifier = modifier)
                }
            }
        }
    }
}
