package net.sigmabeta.chipbox.features.top

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltNavGraphViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.insets.LocalWindowInsets
import net.sigmabeta.chipbox.core.components.ChipboxNavBar
import net.sigmabeta.chipbox.features.artists.ArtistsScreen
import net.sigmabeta.chipbox.features.games.GamesScreen

@Composable
fun TopScreen() {
    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        val navController = rememberNavController()

        NavHost(
            navController,
            startDestination = "games",
            builder = navGraph(),
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth()
        )

        val insets = LocalWindowInsets.current

        ChipboxNavBar(insets) { destination ->
            navController.navigate(destination) {
                launchSingleTop = true
                popUpTo("games") { }
            }
        }
    }
}

@Composable
private fun navGraph(): NavGraphBuilder.() -> Unit = {
    composable("games") { GamesScreen(hiltViewModel()) }
    composable("artists") { ArtistsScreen(hiltViewModel()) }
    composable("playlists") { Text("Todo, lol") }
}