package net.sigmabeta.chipbox.features.artist_detail

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
import net.sigmabeta.chipbox.core.components.ArtistDetailListItem
import net.sigmabeta.chipbox.core.components.ArtistTrackListItem
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.repository.Data
import timber.log.Timber


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistDetailScreen(artistDetailViewModel: ArtistDetailViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val insets = LocalWindowInsets.current
    val density = LocalDensity.current

    val artistsFlow = artistDetailViewModel.artistData()
    val artistFlowLifecycleAware = remember(artistsFlow, lifecycleOwner) {
        artistsFlow.flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
    }

    when (val artistsData = artistFlowLifecycleAware.collectAsState(Data.Empty).value) {
        is Data.Succeeded -> ArtistContent(
            artistsData.data!!,
            insets,
            density
        )
        is Data.Failed -> ArtistErrorState(artistsData.message)
        Data.Empty -> ArtistEmptyState()
        Data.Loading -> ArtistLoadingState()
    }
}

@Composable
fun ArtistContent(
    artist: Artist,
    insets: WindowInsets,
    density: Density
) {
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

@Composable
fun ArtistErrorState(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(64.dp)
    )
}

@Composable
fun ArtistEmptyState() {
    Text(
        text = "Empty",
        modifier = Modifier.padding(64.dp)
    )
}

@Composable
fun ArtistLoadingState() {
    Text(
        text = "Loading",
        modifier = Modifier.padding(64.dp)
    )
}