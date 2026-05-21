package net.sigmabeta.chipbox.appui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.playerstatus.PLAYER_STATUS_ANIM_DURATION_MS
import net.sigmabeta.chipbox.playerstatus.PlayerStatus
import net.sigmabeta.chipbox.playerstatus.PlayerStatusReservedHeight
import net.sigmabeta.chipbox.ui.chrome.LocalChipboxEventSink
import net.sigmabeta.chipbox.ui.chrome.LocalChromeController
import net.sigmabeta.chipbox.ui.chrome.LocalTitleBarController
import net.sigmabeta.chipbox.ui.components.CrossfadeText
import net.sigmabeta.sage.android.ui.list.LocalListBottomInset

private val NAV_RAIL_MIN_WIDTH = 480.dp

/**
 * Root of the outer Voyager Navigator owned by [ChipboxAppUi]. Renders the chrome
 * (TopAppBar + NavigationSuiteScaffold + PlayerStatus overlay) and a [TabNavigator] for
 * the three top-level tabs. Pushing a screen onto the outer Navigator above this root
 * (currently only [NowPlayingScreen]) replaces the whole tab UI on screen — the natural
 * "full-screen overlay" shape for NowPlaying without needing [LocalChromeController] to
 * hide the bars.
 *
 * Deep navigation *within* a tab is handled by each tab's inner Navigator (see
 * `TabNavigatorContent` in [ChipboxScreens]); the inner sink rebind there short-circuits
 * `NavigateTo` / `NavigateBack` to the tab's own stack before the outer sink ever sees
 * the event.
 */
internal object ChipboxTabsScreen : Screen {
    override val key: ScreenKey = "ChipboxTabsScreen"

    @OptIn(ExperimentalMaterial3Api::class)
    @Suppress("LongMethod")
    @Composable
    override fun Content() {
        // Outer Navigator + outer ChipboxEvent sink + shared SnackbarHostState live in
        // [ChipboxAppUi] so they survive while NowPlayingScreen (pushed onto the outer
        // Navigator) replaces this screen as the active stack top.
        val outerNavigator = LocalNavigator.currentOrThrow
        val snackbarHostState = LocalAppSnackbarHostState.current

        // Tracks the active tab's inner Navigator so the TopAppBar back arrow can pop deep
        // destinations within a tab — see [ActiveTabNavigator] for the registration shape.
        val activeTabNavigator = remember { ActiveTabNavigator() }

        val titleBar = LocalTitleBarController.current.state
        val chrome = LocalChromeController.current.state

        val topAppBarState = rememberTopAppBarState()
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)

        var playerStatusVisible by remember { mutableStateOf(false) }
        val navBarBottomInset =
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val navHostBottomInset by animateDpAsState(
            targetValue = if (playerStatusVisible && chrome.showPlayerStatus) {
                PlayerStatusReservedHeight + navBarBottomInset
            } else {
                0.dp
            },
            animationSpec = tween(PLAYER_STATUS_ANIM_DURATION_MS),
            label = "ChipboxTabsScreen.navHostBottomInset",
        )

        // `BoxWithConstraints` is the cross-platform replacement for
        // `LocalConfiguration.current.screenWidthDp` (Android-only) — the latter has no
        // analog on the JVM/desktop target. The reported `maxWidth` is the available width
        // inside this composable, which is the full window once `Box` fills it.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layoutType = if (maxWidth >= NAV_RAIL_MIN_WIDTH) {
                NavigationSuiteType.NavigationRail
            } else {
                NavigationSuiteType.NavigationBar
            }

            CompositionLocalProvider(LocalActiveTabNavigator provides activeTabNavigator) {
                TabNavigator(LibraryTab) { tabNavigator ->
                    // Reset the TopAppBar scroll offset on tab switch (parity with the
                    // AndroidX backStackEntry-keyed LaunchedEffect the old shell used).
                    LaunchedEffect(tabNavigator.current.key) {
                        topAppBarState.contentOffset = 0f
                        topAppBarState.heightOffset = 0f
                    }

                    NavigationSuiteScaffold(
                        navigationSuiteItems = navItems(tabNavigator),
                        layoutType = if (chrome.showNavBar) {
                            layoutType
                        } else {
                            NavigationSuiteType.None
                        },
                    ) {
                        Scaffold(
                            topBar = {
                                AnimatedVisibility(
                                    visible = chrome.showTopBar,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut(),
                                ) {
                                    TopAppBar(
                                        title = {
                                            CrossfadeText(
                                                text = titleBar.title.orEmpty(),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                textModifier = Modifier.basicMarquee(),
                                            )
                                        },
                                        navigationIcon = {
                                            TopAppBarNavIcon(
                                                shouldShowBack = titleBar.shouldShowBack,
                                                onMenu = {
                                                    tabNavigator.current = SettingsTab
                                                },
                                            )
                                        },
                                        scrollBehavior = scrollBehavior,
                                    )
                                }
                            },
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier.nestedScroll(
                                scrollBehavior.nestedScrollConnection,
                            ),
                        ) { padding ->
                            CompositionLocalProvider(
                                LocalListBottomInset provides navHostBottomInset,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(padding),
                                ) {
                                    CurrentTab()
                                    AnimatedVisibility(
                                        visible = chrome.showPlayerStatus,
                                        enter = slideInVertically(initialOffsetY = { it }),
                                        exit = slideOutVertically(targetOffsetY = { it }),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .windowInsetsPadding(
                                                WindowInsets.navigationBars,
                                            ),
                                    ) {
                                        PlayerStatus(
                                            onVisibleChange = { playerStatusVisible = it },
                                            // Push onto the *outer* Navigator so NowPlaying
                                            // covers the tab UI entirely. The tab's inner
                                            // sink would push NowPlaying inside the active
                                            // tab — wrong shape for a full-screen player.
                                            onClick = {
                                                outerNavigator.push(NowPlayingScreen)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pops the *active tab's* inner Navigator on back, falling back to the outer Navigator if
 * for some reason no tab is currently registered. [LocalChipboxEventSink] at this scope
 * is the outer sink, which would only pop the outer Navigator — not what the user wants
 * when at a deep destination inside a tab. Hardware back already works because Voyager
 * routes it to the innermost active Navigator directly.
 */
@Composable
private fun TopAppBarNavIcon(shouldShowBack: Boolean, onMenu: () -> Unit) {
    val activeTabNavigator = LocalActiveTabNavigator.current
    val outerSink = LocalChipboxEventSink.current
    if (shouldShowBack) {
        IconButton(
            onClick = {
                val tabNav = activeTabNavigator.navigator
                if (tabNav != null && tabNav.canPop) {
                    tabNav.pop()
                } else {
                    outerSink(ChipboxEvent.NavigateBack)
                }
            },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
            )
        }
    } else {
        IconButton(onClick = onMenu) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = null,
            )
        }
    }
}

private fun navItems(tabNavigator: TabNavigator): NavigationSuiteScope.() -> Unit = {
    AllTabs.forEach { tab ->
        item(
            selected = tabNavigator.current.key == tab.key,
            onClick = { tabNavigator.current = tab },
            // `Tab.options` is `@Composable get` — read it inside the composable closures
            // (icon/label), not in the outer non-composable lambda body.
            icon = { Icon(painter = tab.options.icon!!, contentDescription = null) },
            label = { Text(tab.options.title) },
        )
    }
}

/**
 * Builds the outer (app-level) [ChipboxEvent] sink. Platform-side effects come in as
 * callbacks ([onOpenUrl], [onCopyToClipboard]) so this composable lives in commonMain;
 * each host app fills in the Android (`Intent.ACTION_VIEW` / `ClipboardManager`) or
 * JVM (`Desktop.browse` / `Toolkit.getDefaultToolkit().systemClipboard`) implementation.
 *
 * `NavigateTo` / `NavigateBack` push/pop the *outer* Navigator — the per-tab sink
 * override in `TabNavigatorContent` short-circuits these for deep destinations so they
 * stay inside a tab; only screens explicitly pushed onto the outer Navigator
 * (NowPlaying) ride this sink for their own NavigateBack.
 */
@Suppress("LongParameterList")
internal fun buildOuterSink(
    snackbarHostState: SnackbarHostState,
    snackbarScope: CoroutineScope,
    onNavigateTo: (Any) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyToClipboard: (label: String, text: String) -> Unit,
): (ChipboxEvent) -> Unit = { event ->
    when (event) {
        is ChipboxEvent.NavigateTo -> onNavigateTo(event.destination)

        ChipboxEvent.NavigateBack -> onNavigateBack()

        is ChipboxEvent.OpenUrl -> onOpenUrl(event.url)

        is ChipboxEvent.ShowSnackbar -> snackbarScope.launch {
            snackbarHostState.showSnackbar(
                message = event.message,
                withDismissAction = event.withDismissAction,
                duration = SnackbarDuration.Short,
            )
        }

        is ChipboxEvent.CopyToClipboard -> {
            onCopyToClipboard(event.label, event.text)
            snackbarScope.launch {
                snackbarHostState.showSnackbar(
                    message = "${event.label} copied to clipboard",
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                )
            }
        }

        // Screen-local effects intercepted by their owning route (see SettingsRoute on
        // Android — SAF folder picker); anything reaching here is a routing bug, but
        // no-op rather than crash.
        ChipboxEvent.PickFolder -> Unit
    }
}
