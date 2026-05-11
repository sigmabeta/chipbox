package net.sigmabeta.chipbox.appui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import net.sigmabeta.chipbox.appcomm.ChipboxNavEvent
import net.sigmabeta.chipbox.appui.screens.SearchScreen
import net.sigmabeta.chipbox.appui.screens.SettingsScreen
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

@Composable
fun ChipboxNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val onNavEvent: (ChipboxNavEvent) -> Unit = { event ->
        when (event) {
            is ChipboxNavEvent.NavigateTo -> navController.navigate(event.destination)
            ChipboxNavEvent.NavigateBack -> { navController.popBackStack() }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Library,
        modifier = modifier,
    ) {
        composable<Library> { LibraryRoute(onNavEvent) }
        composable<Search> { SearchScreen() }
        composable<Settings> { SettingsScreen() }
        composable<BrowseByGame> { BrowseByGameRoute(onNavEvent) }
        composable<BrowseByArtist> { BrowseByArtistRoute() }
        composable<BrowseAllTracks> { BrowseAllTracksRoute() }
        composable<GameDetail> { GameDetailRoute(onNavEvent) }
    }
}
