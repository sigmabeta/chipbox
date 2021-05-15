package net.sigmabeta.chipbox.features.top

import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltNavGraphViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.insets.navigationBarsPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import net.sigmabeta.chipbox.core.components.NavButton
import net.sigmabeta.chipbox.features.artists.ArtistsScreen
import net.sigmabeta.chipbox.features.games.GamesScreen
import net.sigmabeta.chipbox.features.games.GamesViewModel
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Inject

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
        Card(
            elevation = 6.dp,
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
        ) {
            Row(
                Modifier
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                content = navButtons(navController)
            )
        }
    }
}

@Composable
private fun navGraph(): NavGraphBuilder.() -> Unit = {
    composable("games") { GamesScreen(hiltNavGraphViewModel()) }
    composable("artists") { ArtistsScreen() }
}

@Composable
private fun navButtons(navController: NavHostController): @Composable() (RowScope.() -> Unit) =
    {
        NavButton(navController, "games", "Games", Modifier.weight(1.0f))
        NavButton(navController, "artists", "Artists", Modifier.weight(1.0f))
    }
