package net.sigmabeta.chipbox.features.games

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.core.components.GameCard
import net.sigmabeta.chipbox.models.Game
import timber.log.Timber

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamesScreen(gamesViewModel: GamesViewModel) {
    val games: List<Game> by gamesViewModel.games.observeAsState(emptyList())

    LazyVerticalGrid(GridCells.Adaptive(minSize = 192.dp)) {
        items(games) { game ->
            GameCard(
                game.title,
                game.title.reversed(),
                "https://randomfox.ca/images/${game.title.hashCode() % 25}.jpg",
                0,
            ) {
                Timber.i("Clicked game: ${game.title}")
            }
        }
    }
}
