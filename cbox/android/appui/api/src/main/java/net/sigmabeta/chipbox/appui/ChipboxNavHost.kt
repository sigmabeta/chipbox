package net.sigmabeta.chipbox.appui

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.features.search.Search
import net.sigmabeta.chipbox.features.search.real.SearchRoute
import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracks
import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracksRoute
import net.sigmabeta.chipbox.features.browsebyartist.BrowseByArtist
import net.sigmabeta.chipbox.features.browsebyartist.BrowseByArtistRoute
import net.sigmabeta.chipbox.features.browsebygame.BrowseByGame
import net.sigmabeta.chipbox.features.browsebygame.BrowseByGameRoute
import net.sigmabeta.chipbox.features.browsebyplatform.BrowseByPlatform
import net.sigmabeta.chipbox.features.browsebyplatform.BrowseByPlatformRoute
import net.sigmabeta.chipbox.features.gamesforplatform.GamesForPlatform
import net.sigmabeta.chipbox.features.gamesforplatform.GamesForPlatformRoute
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetail
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetailRoute
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetailRoute
import net.sigmabeta.chipbox.features.library.Library
import net.sigmabeta.chipbox.features.library.LibraryRoute
import net.sigmabeta.chipbox.features.nowplaying.NowPlaying
import net.sigmabeta.chipbox.features.nowplaying.real.NowPlayingRoute
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatusEntryPoint
import net.sigmabeta.chipbox.features.settings.Settings
import net.sigmabeta.chipbox.features.settings.SettingsRoute
import net.sigmabeta.chipbox.ui.chrome.LocalChromeController
import net.sigmabeta.chipbox.ui.chrome.ScreenChrome

@Composable
fun ChipboxNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    snackbarScope: CoroutineScope,
    playbackStatusEntryPoint: PlaybackStatusEntryPoint,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val onEvent: (ChipboxEvent) -> Unit = { event ->
        when (event) {
            is ChipboxEvent.NavigateTo -> navController.navigate(event.destination)
            ChipboxEvent.NavigateBack -> { navController.popBackStack() }
            is ChipboxEvent.OpenUrl -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            is ChipboxEvent.ShowSnackbar -> snackbarScope.launch {
                snackbarHostState.showSnackbar(
                    message = event.message,
                    withDismissAction = event.withDismissAction,
                    duration = SnackbarDuration.Short,
                )
            }
            // Screen-local effects intercepted by their owning route (see SettingsRoute);
            // anything that reaches here is a routing bug, but no-op rather than crash.
            ChipboxEvent.PickFolder -> Unit
        }
    }

    NavHost(
        navController = navController,
        startDestination = Library,
        modifier = modifier,
    ) {
        chipboxComposable<Library> { LibraryRoute(onEvent) }
        chipboxComposable<Search> { SearchRoute(onEvent) }
        chipboxComposable<Settings> { SettingsRoute(onEvent) }
        chipboxComposable<NowPlaying> { NowPlayingRoute(onEvent) }
        chipboxComposable<BrowseByGame> { BrowseByGameRoute(onEvent) }
        chipboxComposable<BrowseByPlatform> { BrowseByPlatformRoute(onEvent) }
        chipboxComposable<GamesForPlatform> { GamesForPlatformRoute(onEvent) }
        chipboxComposable<BrowseByArtist> { BrowseByArtistRoute(onEvent) }
        chipboxComposable<BrowseAllTracks> { BrowseAllTracksRoute(onEvent) }
        chipboxComposable<GameDetail> { GameDetailRoute(onEvent) }
        chipboxComposable<ArtistDetail> { ArtistDetailRoute(onEvent) }
        playbackStatusEntryPoint.register(this, onEvent)
    }
}

/**
 * Equivalent to `composable<T> { content() }` but resets [ScreenChrome] to its default on entry.
 * Screens that want non-default chrome override it inside `content`; their `LaunchedEffect`
 * composes after this one so the order — reset, then per-screen override — is deterministic
 * across both navigation and configuration changes. Keeping the reset in the destination's own
 * composition scope (rather than in the shell, keyed on a `backStackEntry` that transitions
 * `null → actual` after recreation) avoids racing the screen's chrome push.
 */
private inline fun <reified T : Any> NavGraphBuilder.chipboxComposable(
    noinline content: @Composable () -> Unit,
) {
    composable<T> {
        val controller = LocalChromeController.current
        LaunchedEffect(Unit) { controller.set(ScreenChrome.Default) }
        content()
    }
}
