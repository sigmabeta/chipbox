package net.sigmabeta.chipbox.features.games

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.WindowInsets
import net.sigmabeta.chipbox.core.components.GameCard
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Data
import timber.log.Timber

@Composable
fun GamesScreen(gamesViewModel: GamesViewModel, navigateAction: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val insets = LocalWindowInsets.current
    val density = LocalDensity.current

    val gamesFlow = gamesViewModel.gamesData()
    val gameFlowLifecycleAware = remember(gamesFlow, lifecycleOwner) {
        gamesFlow.flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
    }

    when (val gamesData = gameFlowLifecycleAware.collectAsState(Data.Empty).value) {
        is Data.Succeeded -> GamesList(
            gamesData.data,
            insets,
            density,
            navigateAction
        )
        is Data.Failed -> ErrorState(gamesData.message)
        Data.Empty -> EmptyState()
        Data.Loading -> LoadingState()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamesList(
    games: List<Game>,
    insets: WindowInsets,
    density: Density,
    navigateAction: (String) -> Unit
) {
    LazyVerticalGrid(
        GridCells.Adaptive(minSize = 192.dp),
        contentPadding = with(density) {
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
                0
            ) {
                Timber.i("Clicked game: ${game.title}")
                navigateAction("game/${game.id}")
            }
        }
    }
}

@Composable
fun ErrorState(message: String) {
    Text(
        text = message
    )
}

@Composable
fun EmptyState() {
    Text(
        text = "Empty"
    )
}

@Composable
fun LoadingState() {
    Text(
        text = "Loading"
    )
}

