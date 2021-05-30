package net.sigmabeta.chipbox.activities

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.insets.LocalWindowInsets
import net.sigmabeta.chipbox.core.components.ChipboxNavBar
import net.sigmabeta.chipbox.navigation.ComposableOutput
import net.sigmabeta.chipbox.navigation.destinations

@Composable
fun TopScreen() {
    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()

        NavHost(
            navController,
            startDestination = "games",
            builder = navGraph(navController),
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth()
        )

        val insets = LocalWindowInsets.current

        ChipboxNavBar(navBackStackEntry?.destination?.route ?: "", insets) { destination ->
            navController.navigate(destination) {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationRoute ?: "games") { }
            }
        }
    }
}

@Composable
private fun navGraph(navController: NavController): NavGraphBuilder.() -> Unit = {
    val destinationList = destinations { navController.navigate(it) }

    destinationList.forEach { destination ->
        composable(destination.name) {
            ComposableOutput(destination, hiltViewModel())
        }
    }
//    composable("games") {
//        EntryAnimation {
//            GamesScreen(hiltViewModel()) { navController.navigate("game/$it") }
//        }
//    }
//    composable("artists") {
//        EntryAnimation {
//            ArtistsScreen(hiltViewModel()) { navController.navigate("artist/$it") }
//        }
//    }
//    composable("playlists") {
//        EntryAnimation {
//            Text(
//                "\n\n\n\n\n\n\nTodo, lol",
//                modifier = Modifier
//                    .fillMaxSize()
//            )
//        }
//    }
//    composable(
//        "game/{gameId}",
//        arguments = listOf(navArgument("gameId") { type = NavType.LongType })
//    ) {
//        EntryAnimation {
//            val viewModel = hiltViewModel<GameDetailViewModel>()
//            val id = it.arguments?.getLong("gameId")!!
//            viewModel.arguments = GameDetailArguments(id)
//            GameDetailScreen(viewModel)
//        }
//    }
//    composable(
//        "artist/{artistId}",
//        arguments = listOf(navArgument("artistId") { type = NavType.LongType })
//    ) {
//        EntryAnimation {
//            val viewModel = hiltViewModel<ArtistDetailViewModel>()
//            val id = it.arguments?.getLong("artistId")!!
//            viewModel.arguments = ArtistDetailArguments(id)
//            ArtistDetailScreen(viewModel)
//        }
//    }
}

