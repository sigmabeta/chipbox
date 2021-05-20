package net.sigmabeta.chipbox.features.games

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.insets.LocalWindowInsets
import net.sigmabeta.chipbox.core.components.GameCard
import net.sigmabeta.chipbox.models.Game
import timber.log.Timber

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamesScreen(gamesViewModel: GamesViewModel) {
    val games: List<Game> by gamesViewModel.games.observeAsState(emptyList())
    val insets = LocalWindowInsets.current

    LazyVerticalGrid(
        GridCells.Adaptive(minSize = 192.dp),
        contentPadding = with(LocalDensity.current) {
            PaddingValues(
                insets.navigationBars.left.toDp(),
                insets.statusBars.top.toDp(),
                insets.navigationBars.right.toDp(),
                0.toDp(),
            )
        },
    ) {
        items(
            items = games,
        ) { game ->
            val artist = when (game.artists?.size ?: 0) {
                0 -> stringResource(id = R.string.caption_unknown_artist)
                1 -> game.artists!!.first().name // Tools insist this can be null here, but it can't.
                else -> stringResource(id = R.string.caption_various_artists)
            }

            GameCard(
                game.title,
                artist,
                game.photoUrl ?: "",
                0,
            ) {
                Timber.i("Clicked game: ${game.title}")
            }
        }
    }
}
