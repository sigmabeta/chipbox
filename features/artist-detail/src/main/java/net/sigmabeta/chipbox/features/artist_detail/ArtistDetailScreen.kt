package net.sigmabeta.chipbox.features.artist_detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalDensity
import com.google.accompanist.insets.LocalWindowInsets
import net.sigmabeta.chipbox.core.components.ArtistDetailListItem
import net.sigmabeta.chipbox.core.components.ArtistTrackListItem
import net.sigmabeta.chipbox.models.Artist
import timber.log.Timber

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistDetailScreen(artistDetailViewModel: ArtistDetailViewModel) {
    val artist: Artist? by artistDetailViewModel.artist.observeAsState(null)
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
            ArtistDetailListItem(
                artist!!
            )
        }

        items(items = artist?.tracks ?: emptyList()) {
            ArtistTrackListItem(track = it) {
                Timber.i("Clicked track: ${it.title}")
            }
        }
    }
}
