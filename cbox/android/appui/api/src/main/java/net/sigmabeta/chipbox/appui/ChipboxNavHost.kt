package net.sigmabeta.chipbox.appui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import net.sigmabeta.chipbox.appui.screens.SearchScreen
import net.sigmabeta.chipbox.appui.screens.SettingsScreen
import net.sigmabeta.chipbox.features.library.LibraryRoute

@Composable
fun ChipboxNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Library,
        modifier = modifier,
    ) {
        composable<Library> { LibraryRoute() }
        composable<Search> { SearchScreen() }
        composable<Settings> { SettingsScreen() }
    }
}
