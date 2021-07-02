package net.sigmabeta.chipbox.features.artists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.accompanist.insets.LocalWindowInsets
import net.sigmabeta.chipbox.core.components.ArtistCard
import net.sigmabeta.chipbox.models.Artist
import timber.log.Timber

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistsScreen(artistsViewModel: ArtistsViewModel, navigateAction: (String) -> Unit) {
    val artists: List<Artist> by artistsViewModel.artists.observeAsState(emptyList())
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
            items = artists,
        ) { artist ->
            ArtistCard(
                artist.name,
                artist.photoUrl ?: "",
                0,
            ) {
                Timber.i("Clicked artist: ${artist.name}")
                navigateAction("artist/${artist.id}")
            }
        }
    }
}