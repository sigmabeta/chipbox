package net.sigmabeta.chipbox.activities

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.*
import com.google.accompanist.insets.LocalWindowInsets
import net.sigmabeta.chipbox.core.components.ChipboxNavBar
import net.sigmabeta.chipbox.features.artist_detail.ArtistDetailArguments
import net.sigmabeta.chipbox.features.artist_detail.ArtistDetailScreen
import net.sigmabeta.chipbox.features.artist_detail.ArtistDetailViewModel
import net.sigmabeta.chipbox.features.artists.ArtistsScreen
import net.sigmabeta.chipbox.features.game_detail.GameDetailArguments
import net.sigmabeta.chipbox.features.game_detail.GameDetailScreen
import net.sigmabeta.chipbox.features.game_detail.GameDetailViewModel
import net.sigmabeta.chipbox.features.games.GamesScreen

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
    composable("games") {
        EntryAnimation {
            GamesScreen(hiltViewModel()) { navController.navigate("game/$it") }
        }
    }
    composable("artists") {
        EntryAnimation {
            ArtistsScreen(hiltViewModel()) { navController.navigate("artist/$it") }
        }
    }
    composable("playlists") {
        EntryAnimation {
            Text(
                "\n\n\n\n\n\n\nTodo, lol",
                modifier = Modifier
                    .fillMaxSize()
            )
        }
    }
    composable(
        "game/{gameId}",
        arguments = listOf(navArgument("gameId") { type = NavType.LongType })
    ) {
        EntryAnimation {
            val viewModel = hiltViewModel<GameDetailViewModel>()
            val id = it.arguments?.getLong("gameId")!!
            viewModel.arguments = GameDetailArguments(id)
            GameDetailScreen(viewModel)
        }
    }
    composable(
        "artist/{artistId}",
        arguments = listOf(navArgument("artistId") { type = NavType.LongType })
    ) {
        EntryAnimation {
            val viewModel = hiltViewModel<ArtistDetailViewModel>()
            val id = it.arguments?.getLong("artistId")!!
            viewModel.arguments = ArtistDetailArguments(id)
            ArtistDetailScreen(viewModel)
        }
    }
}

// TODO Hacky AF. Once there's a more better API for this, use that.
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EntryAnimation(content: @Composable () -> Unit) {
    val visibleState = remember {
        MutableTransitionState(
            initialState = false
        ).apply {
            targetState = true
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInHorizontally(initialOffsetX = animationOffset()) + fadeIn(),
        exit = ExitTransition.None
    )
    {
        content()
    }
}

@Composable
private fun animationOffset(): (fullWidth: Int) -> Int = {
    it / 4
}