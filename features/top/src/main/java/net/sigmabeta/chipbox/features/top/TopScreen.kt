package net.sigmabeta.chipbox.features.top

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    composable("games") {
        EntryAnimation {
            GamesScreen(hiltViewModel())
        }
    }
    composable("artists") {
        EntryAnimation {
            ArtistsScreen(hiltViewModel())
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