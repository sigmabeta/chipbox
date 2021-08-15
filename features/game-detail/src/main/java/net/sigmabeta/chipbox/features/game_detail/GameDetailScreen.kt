package net.sigmabeta.chipbox.features.game_detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.WindowInsets
import net.sigmabeta.chipbox.core.components.GameDetailListItem
import net.sigmabeta.chipbox.core.components.GameTrackListItem
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Data
import timber.log.Timber

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameDetailScreen(gameDetailViewModel: GameDetailViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val insets = LocalWindowInsets.current
    val density = LocalDensity.current

    val gamesFlow = gameDetailViewModel.gameData()
    val gameFlowLifecycleAware = remember(gamesFlow, lifecycleOwner) {
        gamesFlow.flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
    }

    when (val gamesData = gameFlowLifecycleAware.collectAsState(Data.Empty).value) {
        is Data.Succeeded -> GameContent(
            gamesData.data!!,
            insets,
            density
        )
        is Data.Failed -> GameErrorState(gamesData.message)
        Data.Empty -> GameEmptyState()
        Data.Loading -> GameLoadingState()
    }
}

@Composable
fun GameContent(
    game: Game,
    insets: WindowInsets,
    density: Density
) {
    LazyColumn(
        contentPadding = with(density) {
            PaddingValues(
                insets.navigationBars.left.toDp(),
                insets.statusBars.top.toDp(),
                insets.navigationBars.right.toDp(),
                0.toDp(),
            )
        },
    ) {
        item {
            GameDetailListItem(
                game
            )
        }

        items(items = game.tracks ?: emptyList()) {
            GameTrackListItem(track = it) {
                Timber.i("Clicked track: ${it.title}")
            }
        }
    }

}

@Composable
fun GameErrorState(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(64.dp)
    )
}

@Composable
fun GameEmptyState() {
    Text(
        text = "Empty",
        modifier = Modifier.padding(64.dp)
    )
}

@Composable
fun GameLoadingState() {
    Text(
        text = "Loading",
        modifier = Modifier.padding(64.dp)
    )
}
