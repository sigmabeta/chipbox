package net.sigmabeta.chipbox.features.game_detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalDensity
import com.google.accompanist.insets.LocalWindowInsets
import net.sigmabeta.chipbox.core.components.GameCard
import net.sigmabeta.chipbox.core.components.GameTrackListItem
import net.sigmabeta.chipbox.models.Game
import timber.log.Timber

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameDetailScreen(gameDetailViewModel: GameDetailViewModel) {
    val game: Game? by gameDetailViewModel.game.observeAsState(null)
    val insets = LocalWindowInsets.current

    LazyColumn(
        contentPadding = with(LocalDensity.current) {
            PaddingValues(
                insets.navigationBars.left.toDp(),
                insets.statusBars.top.toDp(),
                insets.navigationBars.right.toDp(),
                0.toDp(),
            )
        },
    ) {
        item {
            GameCard(
                title = game?.title ?: "Unknown",
                artist = game?.artists?.first()?.name ?: "Test",
                image = game?.photoUrl ?: ""
            ) {
                Timber.i("Clicked game: ${game?.title}")
            }
        }

        items(items = game?.tracks ?: emptyList()) {
            GameTrackListItem(track = it) {
                Timber.i("Clicked track: ${it.title}")
            }
        }
    }
}
