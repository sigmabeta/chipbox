package net.sigmabeta.chipbox.appui

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.appui.screens.SearchScreen
import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracks
import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracksRoute
import net.sigmabeta.chipbox.features.browsebyartist.BrowseByArtist
import net.sigmabeta.chipbox.features.browsebyartist.BrowseByArtistRoute
import net.sigmabeta.chipbox.features.browsebygame.BrowseByGame
import net.sigmabeta.chipbox.features.browsebygame.BrowseByGameRoute
import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.features.gamedetail.GameDetailRoute
import net.sigmabeta.chipbox.features.library.Library
import net.sigmabeta.chipbox.features.library.LibraryRoute
import net.sigmabeta.chipbox.features.settings.Settings
import net.sigmabeta.chipbox.features.settings.SettingsRoute

@Composable
fun ChipboxNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    snackbarScope: CoroutineScope,
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
        }
    }

    NavHost(
        navController = navController,
        startDestination = Library,
        modifier = modifier,
    ) {
        composable<Library> { LibraryRoute(onEvent) }
        composable<Search> { SearchScreen() }
        composable<Settings> { SettingsRoute(onEvent) }
        composable<BrowseByGame> { BrowseByGameRoute(onEvent) }
        composable<BrowseByArtist> { BrowseByArtistRoute() }
        composable<BrowseAllTracks> { BrowseAllTracksRoute() }
        composable<GameDetail> { GameDetailRoute(onEvent) }
    }
}
