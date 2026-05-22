package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import dev.zacsweers.metrox.viewmodel.metroViewModel
import net.sigmabeta.chipbox.common.ui.chrome.api.ChromeController
import net.sigmabeta.chipbox.common.ui.chrome.api.LocalChipboxEventSink
import net.sigmabeta.chipbox.common.ui.chrome.api.LocalChromeController
import net.sigmabeta.chipbox.common.ui.chrome.api.LocalTitleBarController
import net.sigmabeta.chipbox.common.ui.chrome.api.TitleBarController
import net.sigmabeta.chipbox.ui.theme.api.ChipboxTheme
import net.sigmabeta.chipbox.ui.theme.api.tokens.ChipboxFontDefaults

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
@Composable
fun ChipboxAppUi(
    onOpenUrl: (String) -> Unit,
    onCopyToClipboard: (label: String, text: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = ChipboxFontDefaults.Brand
    val plain = ChipboxFontDefaults.Plain
    ChipboxTheme(
        brand = brand.toFontFamily(),
        plain = plain.toFontFamily(),
        brandScale = brand.scaleFactor,
        plainScale = plain.scaleFactor,
    ) {
        // Eagerly materialize the appui-scoped VM so Metro multibinding misses surface at
        // launch rather than at the first screen entry.
        metroViewModel<ChipboxAppUiViewModel>()

        val titleBarController = remember { TitleBarController() }
        val chromeController = remember { ChromeController() }
        val snackbarHostState = remember { SnackbarHostState() }
        val snackbarScope = rememberCoroutineScope()

        CompositionLocalProvider(
            LocalTitleBarController provides titleBarController,
            LocalChromeController provides chromeController,
            LocalAppSnackbarHostState provides snackbarHostState,
        ) {
            Navigator(ChipboxTabsScreen) { navigator ->
                val outerSink = remember(
                    navigator,
                    snackbarHostState,
                    snackbarScope,
                    onOpenUrl,
                    onCopyToClipboard,
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
                    )
                }
                CompositionLocalProvider(LocalChipboxEventSink provides outerSink) {
                    SlideTransition(navigator, modifier = modifier)
                }
            }
        }
    }
}
