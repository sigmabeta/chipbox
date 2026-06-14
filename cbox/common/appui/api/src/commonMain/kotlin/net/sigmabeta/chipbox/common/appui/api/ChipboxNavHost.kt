package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
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
import androidx.compose.runtime.DisposableEffect
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
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.ui.Icon as SageIcon
import net.sigmabeta.sage.ui.vector
import net.sigmabeta.chipbox.common.playerstatus.api.PLAYER_STATUS_ANIM_DURATION_MS
import net.sigmabeta.chipbox.common.playerstatus.api.PlayerStatus
import net.sigmabeta.chipbox.common.playerstatus.api.PlayerStatusReservedHeight
import net.sigmabeta.chipbox.common.ui.chrome.api.LocalChipboxEventSink
import net.sigmabeta.chipbox.common.ui.chrome.api.LocalChromeController
import net.sigmabeta.chipbox.common.ui.chrome.api.LocalTitleBarController
import net.sigmabeta.chipbox.common.ui.components.api.CrossfadeText
import net.sigmabeta.sage.ui.list.LocalListBottomInset

private val NAV_RAIL_MIN_WIDTH = 480.dp

/**
 * Root of the outer Voyager Navigator owned by [ChipboxAppUi]. Renders the chrome
 * (TopAppBar + NavigationSuiteScaffold + PlayerStatus overlay) and a [TabNavigator] for
 * the top-level tabs (Home/Library/Search). Settings is reached via the top-bar
 * hamburger button (see [TopAppBarNavIcon]), pushed onto the active tab's deep stack.
 *
 * Deep navigation *within* a tab is handled by each tab's inner Navigator (see
 * `TabNavigatorContent` in [ChipboxScreens]); the inner sink rebind there short-circuits
 * `NavigateTo` / `NavigateBack` to the tab's own stack before the outer sink ever sees
 * the event. [NowPlayingScreen] is pushed onto the active tab's inner Navigator too, so it
 * renders *inside* this Scaffold's content — [LocalChromeController] then hides the top bar
 * and PlayerStatus while leaving the nav bar in place (the pre-Voyager shell behavior).
 */
internal object ChipboxTabsScreen : Screen {
    override val key: ScreenKey = "ChipboxTabsScreen"

    @OptIn(ExperimentalMaterial3Api::class)
    @Suppress("LongMethod")
    @Composable
    override fun Content() {
        // Outer ChipboxEvent sink + shared SnackbarHostState live in [ChipboxAppUi]; the
        // sink survives tab switches and the Scaffold below renders the shared host.
        val outerSink = LocalChipboxEventSink.current
        val snackbarHostState = LocalAppSnackbarHostState.current

        // Tracks the active tab's inner Navigator so back routing can pop deep destinations
        // within a tab — see [ActiveTabNavigator] for the registration shape.
        val activeTabNavigator = remember { ActiveTabNavigator() }

        // Tracks the outer TabNavigator so the back handler (created here, outside the
        // TabNavigator content block) can read/mutate the current tab — see [TabRouter].
        val tabRouter = remember { TabRouter() }

        // The single back-routing handler for the whole shell. Both back affordances report
        // *what happened* — `AppBack` from the TopAppBar up arrow, `DeviceBack` from each tab
        // Navigator's `onBackPressed` (see `TabNavigatorContent`) — and this decides the
        // navigation: pop the active tab's deep stack, else (if on a non-Home tab root)
        // switch to the Home tab, else fall back to the outer Navigator.
        val appActionSink = remember(activeTabNavigator, tabRouter, outerSink) {
            ActionSink { action ->
                when (action) {
                    SageAction.AppBack, SageAction.DeviceBack -> {
                        val tabNav = activeTabNavigator.navigator
                        val outerTabNav = tabRouter.tabNavigator
                        when {
                            tabNav != null && tabNav.canPop -> tabNav.pop()

                            outerTabNav != null &&
                                outerTabNav.current.key == SearchTab.key -> {
                                tabNav?.popAll()
                                outerTabNav.current = HomeTab
                            }

                            else -> outerSink(ChipboxEvent.NavigateBack)
                        }
                    }

                    else -> Unit
                }
            }
        }

        // Peripheral "back" inputs (Escape, Backspace, mouse back button) feed the same handler as
        // the TopAppBar up arrow and Android system back. Keyboard keys arrive as a flow from the
        // platform's window/activity-level handler (see [LocalPlatformBackKeys]) so they work any
        // time the window is focused; the mouse back button is handled in-composition below (pointer
        // events don't need focus).
        val onBack = remember(appActionSink) { { appActionSink.sendAction(SageAction.DeviceBack) } }
        val platformBackKeys = LocalPlatformBackKeys.current
        if (platformBackKeys != null) {
            LaunchedEffect(platformBackKeys, onBack) {
                platformBackKeys.collect { onBack() }
            }
        }

        // Programmatic navigation into the active tab (UI tests / future deep-links) — push each
        // requested destination onto the active tab's Navigator, the same place an in-tab
        // `NavigateTo` lands, so the pushed screen renders with this scaffold's chrome around it.
        // See [LocalActiveTabDestinations].
        val activeTabDestinations = LocalActiveTabDestinations.current
        val navigationObserver = LocalNavigationObserver.current
        if (activeTabDestinations != null) {
            LaunchedEffect(activeTabDestinations, activeTabNavigator, navigationObserver) {
                activeTabDestinations.collect { destination ->
                    navigationObserver?.invoke(destination)
                    activeTabNavigator.navigator?.push(screenFor(destination))
                }
            }
        }

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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .backMouseButton(onBack),
        ) {
            val layoutType = if (maxWidth >= NAV_RAIL_MIN_WIDTH) {
                NavigationSuiteType.NavigationRail
            } else {
                NavigationSuiteType.NavigationBar
            }

            CompositionLocalProvider(
                LocalActiveTabNavigator provides activeTabNavigator,
                LocalAppActionSink provides appActionSink,
            ) {
                TabNavigator(HomeTab) { tabNavigator ->
                    // Expose the TabNavigator to [appActionSink] so back-handling above can
                    // read the current tab and switch tabs.
                    DisposableEffect(tabNavigator) {
                        tabRouter.tabNavigator = tabNavigator
                        onDispose {
                            if (tabRouter.tabNavigator === tabNavigator) {
                                tabRouter.tabNavigator = null
                            }
                        }
                    }

                    // Re-extend the TopAppBar on *any* navigation — tab switch, deep push, or
                    // pop. Keyed on the active route (current tab + the active tab's top screen),
                    // so it fires whenever the visible screen changes, not just on tab switch.
                    // `lastItem` reads the tab Navigator's SnapshotStateList, so a push/pop within
                    // a tab recomposes this and re-runs the effect (the reactive read `navItems`
                    // already relies on).
                    val activeRouteKey = activeTabNavigator.navigator?.let { nav ->
                        "${tabNavigator.current.key}/${nav.lastItem.key}"
                    } ?: tabNavigator.current.key
                    // Push the visible route into the app-level VM so cross-cutting policy
                    // (e.g. event-honour decisions) can read it. The composed key carries
                    // both the active tab and the top of its deep stack.
                    val appUiViewModel = LocalChipboxAppUiViewModel.current
                    LaunchedEffect(activeRouteKey) {
                        appUiViewModel.setCurrentRoute(activeRouteKey)
                    }
                    LaunchedEffect(activeRouteKey) {
                        // Animate (not snap) the bar back to fully extended, reusing the scroll
                        // behavior's own snap spec so it matches a manual release. contentOffset is
                        // the scroll accumulator (zeroed up front); heightOffset drives the visible
                        // height, so we tween that down to 0. Re-keying cancels this coroutine, so a
                        // fast follow-up navigation restarts the animation from the current height.
                        topAppBarState.contentOffset = 0f
                        val spec = scrollBehavior.snapAnimationSpec
                        if (spec != null) {
                            animate(
                                initialValue = topAppBarState.heightOffset,
                                targetValue = 0f,
                                animationSpec = spec,
                            ) { value, _ -> topAppBarState.heightOffset = value }
                        } else {
                            topAppBarState.heightOffset = 0f
                        }
                    }

                    // Selecting a tab clears that tab's stack and starts a new one. We pop the
                    // *outgoing* (currently active) tab to its root before switching: because
                    // every switch resets the tab being left, each tab's saved state stays at
                    // just its root, so the tab you arrive at is always fresh. Re-tapping the
                    // current tab pops it to root in place. `popAll()` keeps the root and —
                    // unlike `popUntilRoot()` — doesn't reach up into the tab/outer navigators.
                    val selectTab: (Tab) -> Unit = { target ->
                        activeTabNavigator.navigator?.popAll()
                        if (tabNavigator.current.key != target.key) {
                            tabNavigator.current = target
                        }
                    }

                    NavigationSuiteScaffold(
                        navigationSuiteItems = navItems(tabNavigator, activeTabNavigator, selectTab),
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
                                                    activeTabNavigator.navigator
                                                        ?.push(SettingsDeepScreen)
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
                                            // Push onto the *active tab's* inner Navigator so
                                            // NowPlaying renders inside this Scaffold's content
                                            // (nav bar stays; the chrome controller hides the
                                            // top bar + PlayerStatus) — the pre-Voyager shape.
                                            // No-op if no tab is registered yet.
                                            onClick = {
                                                activeTabNavigator.navigator
                                                    ?.push(NowPlayingScreen)
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
 * The TopAppBar up arrow reports an `AppBack` to [LocalAppActionSink], which owns the
 * decision of *which* navigator to pop (active tab's deep stack, else the outer Navigator) —
 * the same handler the Android system back feeds via `DeviceBack`. When there's nothing to
 * go back to, the icon is the hamburger menu instead (pushes Settings onto the active
 * tab's deep stack — Settings is no longer a top-level tab).
 */
@Composable
private fun TopAppBarNavIcon(shouldShowBack: Boolean, onMenu: () -> Unit) {
    val appActionSink = LocalAppActionSink.current
    if (shouldShowBack) {
        IconButton(
            onClick = { appActionSink.sendAction(SageAction.AppBack) },
        ) {
            Icon(
                imageVector = SageIcon.Back.vector(),
                contentDescription = null,
            )
        }
    } else {
        IconButton(onClick = onMenu) {
            Icon(
                imageVector = SageIcon.Menu.vector(),
                contentDescription = null,
            )
        }
    }
}

private fun navItems(
    tabNavigator: TabNavigator,
    activeTabNavigator: ActiveTabNavigator,
    onSelectTab: (Tab) -> Unit,
): NavigationSuiteScope.() -> Unit = {
    // A pushed [TablessScreen] (e.g. NowPlaying) isn't owned by any tab, so show no tab as
    // selected while one is on top of the active tab's stack. Reading the active navigator's
    // `lastItem` here keeps the highlight reactive to push/pop within the tab.
    val onTablessScreen = activeTabNavigator.navigator?.lastItem is TablessScreen
    AllTabs.forEach { tab ->
        item(
            selected = !onTablessScreen && tabNavigator.current.key == tab.key,
            onClick = { onSelectTab(tab) },
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
    onRequestMiniPlayerVisibility: (Boolean) -> Unit,
    onRequestTopBarVisibility: (Boolean) -> Unit,
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

        // Forwarded to the caller — the sink doesn't decide whether to honour the request.
        // Today the host (ChipboxAppUi) just sets the chrome controller's `showPlayerStatus`
        // unconditionally, but the indirection lets a future host gate it (e.g. ignore
        // hide-requests while a "pin mini-player" debug toggle is on).
        is ChipboxEvent.RequestMiniPlayerVisibility -> onRequestMiniPlayerVisibility(event.visible)

        is ChipboxEvent.RequestTopBarVisibility -> onRequestTopBarVisibility(event.visible)

        // Screen-local effects intercepted by their owning route (see SettingsRoute on
        // Android — SAF folder picker); anything reaching here is a routing bug, but
        // no-op rather than crash.
        ChipboxEvent.PickFolder -> Unit
    }
}
