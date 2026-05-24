package net.sigmabeta.chipbox.common.appui.api

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetailRoute
import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracks
import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracksRoute
import net.sigmabeta.chipbox.features.browsebyartist.BrowseByArtist
import net.sigmabeta.chipbox.features.browsebyartist.BrowseByArtistRoute
import net.sigmabeta.chipbox.features.browsebygame.BrowseByGame
import net.sigmabeta.chipbox.features.browsebygame.BrowseByGameRoute
import net.sigmabeta.chipbox.features.browsebyplatform.BrowseByPlatform
import net.sigmabeta.chipbox.features.browsebyplatform.BrowseByPlatformRoute
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetailRoute
import net.sigmabeta.chipbox.features.gamesforplatform.GamesForPlatform
import net.sigmabeta.chipbox.features.gamesforplatform.GamesForPlatformRoute
import net.sigmabeta.chipbox.features.library.Library
import net.sigmabeta.chipbox.features.library.LibraryRoute
import net.sigmabeta.chipbox.features.managelibrary.ManageLibrary
import net.sigmabeta.chipbox.features.managelibrary.ManageLibraryRoute
import net.sigmabeta.chipbox.features.nowplaying.NowPlaying
import net.sigmabeta.chipbox.features.nowplaying.real.NowPlayingRoute
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatus
import net.sigmabeta.chipbox.features.playbackstatus.real.PlaybackStatusRoute
import net.sigmabeta.chipbox.features.search.Search
import net.sigmabeta.chipbox.features.search.real.SearchRoute
import net.sigmabeta.chipbox.features.settings.Settings
import net.sigmabeta.chipbox.features.settings.SettingsRoute
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.strings.api.text
import net.sigmabeta.chipbox.common.ui.chrome.api.LocalChipboxEventSink
import net.sigmabeta.chipbox.common.ui.chrome.api.LocalChromeController
import net.sigmabeta.chipbox.common.ui.chrome.api.ScreenChrome

/**
 * Maps a `ChipboxEvent.NavigateTo(destination)` payload (still the existing `data object` /
 * `data class` markers in each feature's `:api`) to a Voyager [Screen]. Each anonymous
 * wrapper pulls [LocalChipboxEventSink] from the local tab Navigator scope and threads it
 * to the per-feature `XxxRoute` composable.
 *
 * Throws on unknown destinations rather than no-op'ing — silent navigation drops were a
 * frequent source of confusion under the AndroidX nav graph.
 */
internal fun screenFor(destination: Any): Screen = when (destination) {
    Library -> LibraryDeepScreen
    Search -> SearchDeepScreen
    Settings -> SettingsDeepScreen
    ManageLibrary -> ManageLibraryScreen
    PlaybackStatus -> PlaybackStatusScreen
    NowPlaying -> NowPlayingScreen
    BrowseByGame -> BrowseByGameScreen
    BrowseByPlatform -> BrowseByPlatformScreen
    BrowseAllTracks -> BrowseAllTracksScreen
    BrowseByArtist -> BrowseByArtistScreen
    is GameDetail -> GameDetailDeepScreen(destination.id)
    is ArtistDetail -> ArtistDetailDeepScreen(destination.id)
    is GamesForPlatform -> GamesForPlatformDeepScreen(destination.platform)
    else -> error("No Voyager Screen registered for destination $destination")
}

/**
 * Composable scaffolding shared by every tab root and pushed screen: reset [ScreenChrome]
 * to its default on entry so a screen that wants non-default chrome can override it inside
 * `content` and its `LaunchedEffect` composes after this one (deterministic order across
 * navigation + config change — same invariant the AndroidX `chipboxComposable` wrapper kept).
 */
@Composable
private fun ScreenScaffold(content: @Composable () -> Unit) {
    val controller = LocalChromeController.current
    LaunchedEffect(Unit) { controller.set(ScreenChrome.Default) }
    // [WithPerScreenViewModelStore] gives each Screen its own ViewModelStore on JVM (where
    // Voyager doesn't); androidMain is a passthrough since Voyager already does it via
    // AndroidScreenLifecycleOwner. Without this, all screens in the JVM Navigator share the
    // Window's ViewModelStore and `metroViewModel<VM>()` returns the same cached instance —
    // pushing GamesForPlatform(DREAMCAST) then GamesForPlatform(GENESIS) reuses Dreamcast.
    WithPerScreenViewModelStore {
        content()
    }
}

// region Tabs ----------------------------------------------------------------------------------

/**
 * Each [Tab] hosts its own [Navigator] so per-tab back stacks are preserved when the user
 * switches between tabs (AndroidX equivalent: `popUpTo(start) + saveState + restoreState`).
 * The tab rebinds [LocalChipboxEventSink] so feature VMs emitting `NavigateTo` / `NavigateBack`
 * push/pop the *local* tab Navigator while system events (snackbar/clipboard/openUrl/picker)
 * bubble to the app-level sink set up in [ChipboxTabsScreen].
 */
internal object LibraryTab : Tab {

    override val key: ScreenKey = "LibraryTab"

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = LIBRARY_TAB_INDEX,
            title = ChipboxStringId.APPUI_TAB_LIBRARY.text(),
            icon = rememberTabIcon(Icons.Filled.LibraryMusic),
        )

    @Composable
    override fun Content() {
        TabNavigatorContent(LibraryTabRoot)
    }
}

internal object SearchTab : Tab {

    override val key: ScreenKey = "SearchTab"

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = SEARCH_TAB_INDEX,
            title = ChipboxStringId.APPUI_TAB_SEARCH.text(),
            icon = rememberTabIcon(Icons.Filled.Search),
        )

    @Composable
    override fun Content() {
        TabNavigatorContent(SearchTabRoot)
    }
}

internal object SettingsTab : Tab {

    override val key: ScreenKey = "SettingsTab"

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = SETTINGS_TAB_INDEX,
            title = ChipboxStringId.APPUI_TAB_SETTINGS.text(),
            icon = rememberTabIcon(Icons.Filled.Settings),
        )

    @Composable
    override fun Content() {
        TabNavigatorContent(SettingsTabRoot)
    }
}

internal val AllTabs: List<Tab> = listOf(LibraryTab, SearchTab, SettingsTab)

private const val LIBRARY_TAB_INDEX: UShort = 0u
private const val SEARCH_TAB_INDEX: UShort = 1u
private const val SETTINGS_TAB_INDEX: UShort = 2u

@Composable
private fun rememberTabIcon(imageVector: ImageVector) = rememberVectorPainter(imageVector)

/**
 * Per-tab Navigator that bridges [LocalChipboxEventSink] from the app-level outer sink
 * (provided in [ChipboxTabsScreen]) to one that pushes/pops the local tab Navigator.
 * `SlideTransition` matches the AndroidX nav-compose default forward/backward animation.
 *
 * `onBackPressed` routes the Android system back through [LocalAppActionSink] as a
 * `DeviceBack` instead of letting Voyager pop directly — so device back and the app-bar up
 * arrow (`AppBack`) share one handler. Returning `false` declines Voyager's built-in pop,
 * leaving the actual navigation to that handler. Voyager only invokes this while the tab
 * (or its parent) `canPop`, so at a tab root the system back still falls through to exit.
 */
@Composable
private fun TabNavigatorContent(root: Screen) {
    val appActionSink = LocalAppActionSink.current
    Navigator(
        root,
        onBackPressed = {
            appActionSink.sendAction(SageAction.DeviceBack)
            false
        },
    ) { navigator ->
        val outerSink = LocalChipboxEventSink.current
        val activeTabNavigator = LocalActiveTabNavigator.current

        // Expose this tab's Navigator to the chrome ([ChipboxTabsScreen]) so the shell's
        // back handler ([LocalAppActionSink]) can pop the deep stack from outside the tab's
        // scope. On tab switch, Voyager unmounts the old tab's UI (firing onDispose) before
        // mounting the new tab's, so the registration tracks the currently-rendered tab.
        DisposableEffect(navigator) {
            activeTabNavigator.navigator = navigator
            onDispose {
                if (activeTabNavigator.navigator === navigator) {
                    activeTabNavigator.navigator = null
                }
            }
        }

        // Explicit `Unit` return — `navigator.pop()` returns Boolean (true if popped) and
        // `navigator.push()` returns Unit, which Kotlin would otherwise infer as `Any`.
        val sink: (ChipboxEvent) -> Unit = remember(navigator, outerSink) {
            { event ->
                when (event) {
                    is ChipboxEvent.NavigateTo -> navigator.push(screenFor(event.destination))

                    ChipboxEvent.NavigateBack -> {
                        navigator.pop()
                        Unit
                    }

                    else -> outerSink(event)
                }
            }
        }
        CompositionLocalProvider(LocalChipboxEventSink provides sink) {
            SlideTransition(navigator)
        }
    }
}

// endregion

// region Tab root screens (top of each tab's per-tab back stack) -------------------------------

private object LibraryTabRoot : Screen {
    override val key: ScreenKey = "LibraryTabRoot"

    @Composable
    override fun Content() = ScreenScaffold {
        LibraryRoute(onEvent = LocalChipboxEventSink.current)
    }
}

private object SearchTabRoot : Screen {
    override val key: ScreenKey = "SearchTabRoot"

    @Composable
    override fun Content() = ScreenScaffold {
        SearchRoute(onEvent = LocalChipboxEventSink.current)
    }
}

private object SettingsTabRoot : Screen {
    override val key: ScreenKey = "SettingsTabRoot"

    @Composable
    override fun Content() = ScreenScaffold {
        SettingsRoute(onEvent = LocalChipboxEventSink.current)
    }
}

// endregion

// region Deep screens — pushed by `screenFor()` --------------------------------------------------

/**
 * `Library`/`Search`/`Settings` as a *deep push* (not a tab switch): a VM emits
 * `NavigateTo(Library)` and the current tab's Navigator stacks the same root composable.
 * Rare in practice (TopLevelDestinations are usually reached via the tab bar), but kept
 * for parity with the AndroidX graph that registered them as composables.
 */
private object LibraryDeepScreen : Screen {
    @Composable override fun Content() = ScreenScaffold {
        LibraryRoute(onEvent = LocalChipboxEventSink.current)
    }
}

private object SearchDeepScreen : Screen {
    @Composable override fun Content() = ScreenScaffold {
        SearchRoute(onEvent = LocalChipboxEventSink.current)
    }
}

private object SettingsDeepScreen : Screen {
    @Composable override fun Content() = ScreenScaffold {
        SettingsRoute(onEvent = LocalChipboxEventSink.current)
    }
}

/**
 * Library-folder management, pushed from the Settings "Manage Library" row (which replaces the
 * "Add folder to library" row once at least one folder exists). Lists the user's folders and
 * removes one on tap; its own "Add folder to library" CTA reuses the SAF/Swing picker.
 */
private object ManageLibraryScreen : Screen {
    @Composable override fun Content() = ScreenScaffold {
        ManageLibraryRoute(onEvent = LocalChipboxEventSink.current)
    }
}

/**
 * Debug-only playback diagnostics, reached from the Settings debug section (which is itself
 * gated behind the `shouldShowDebug` toggle). Always registered here — the route is only
 * reachable when the gated Settings row emits `NavigateTo(PlaybackStatus)`.
 */
private object PlaybackStatusScreen : Screen {
    @Composable override fun Content() = ScreenScaffold {
        PlaybackStatusRoute(onEvent = LocalChipboxEventSink.current)
    }
}

/**
 * Marker for a pushed [Screen] that isn't owned by any tab. While one is on top of the
 * active tab's stack, the navigation bar shows no tab as selected (see `navItems` in
 * [ChipboxTabsScreen]) — the screen still lives in that tab's back stack; this only clears
 * the selected highlight.
 */
internal interface TablessScreen

/**
 * Pushed onto the *active tab's* inner Navigator (see `PlayerStatus.onClick` in
 * [ChipboxTabsScreen]) so it renders inside the tabs' Scaffold content — the pre-Voyager
 * shape. `NowPlayingRoute` sets [ScreenChrome] to hide the top bar + PlayerStatus while
 * leaving the nav bar, and the Scaffold's own snackbar slot renders snackbars, so this
 * screen needs neither its own bars nor its own host. As a [TablessScreen] it deselects all
 * tabs in the nav bar while it's on top.
 */
internal object NowPlayingScreen : Screen, TablessScreen {
    override val key: ScreenKey = "NowPlaying"

    @Composable override fun Content() = ScreenScaffold {
        NowPlayingRoute(onEvent = LocalChipboxEventSink.current)
    }
}

private object BrowseByGameScreen : Screen {
    @Composable override fun Content() = ScreenScaffold {
        BrowseByGameRoute(onEvent = LocalChipboxEventSink.current)
    }
}

private object BrowseByPlatformScreen : Screen {
    @Composable override fun Content() = ScreenScaffold {
        BrowseByPlatformRoute(onEvent = LocalChipboxEventSink.current)
    }
}

private object BrowseAllTracksScreen : Screen {
    @Composable override fun Content() = ScreenScaffold {
        BrowseAllTracksRoute(onEvent = LocalChipboxEventSink.current)
    }
}

private object BrowseByArtistScreen : Screen {
    @Composable override fun Content() = ScreenScaffold {
        BrowseByArtistRoute(onEvent = LocalChipboxEventSink.current)
    }
}

// Voyager's default `Screen.key` is just the class name (`this::class.multiplatformName`),
// which means two `GameDetailDeepScreen(248)` + `GameDetailDeepScreen(96)` instances share
// the same ViewModelStore (since `metroViewModel` keys the VM by class within the store).
// Result: pushing the second instance reuses the first's VM and its baked-in `@Assisted
// gameId`. Override `key` for every parameterized screen to embed the args so each instance
// gets its own store + its own VM.
private data class GameDetailDeepScreen(val gameId: Long) : Screen {
    override val key: ScreenKey = "GameDetail:$gameId"

    @Composable override fun Content() = ScreenScaffold {
        GameDetailRoute(gameId = gameId, onEvent = LocalChipboxEventSink.current)
    }
}

private data class ArtistDetailDeepScreen(val artistId: Long) : Screen {
    override val key: ScreenKey = "ArtistDetail:$artistId"

    @Composable override fun Content() = ScreenScaffold {
        ArtistDetailRoute(artistId = artistId, onEvent = LocalChipboxEventSink.current)
    }
}

private data class GamesForPlatformDeepScreen(val platform: Platform) : Screen {
    override val key: ScreenKey = "GamesForPlatform:${platform.name}"

    @Composable override fun Content() = ScreenScaffold {
        GamesForPlatformRoute(platform = platform, onEvent = LocalChipboxEventSink.current)
    }
}

// endregion
