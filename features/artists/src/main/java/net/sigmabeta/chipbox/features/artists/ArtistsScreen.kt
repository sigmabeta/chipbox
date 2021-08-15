package net.sigmabeta.chipbox.features.artists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
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
import net.sigmabeta.chipbox.core.components.ArtistCard
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.repository.Data
import timber.log.Timber

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistsScreen(artistsViewModel: ArtistsViewModel, navigateAction: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val insets = LocalWindowInsets.current
    val density = LocalDensity.current

    val artistsFlow = artistsViewModel.artistsData()
    val artistsFlowLifecycleAware = remember(artistsFlow, lifecycleOwner) {
        artistsFlow.flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
    }

    when (val artistsData = artistsFlowLifecycleAware.collectAsState(Data.Empty).value) {
        is Data.Succeeded -> ArtistsList(
            artistsData.data,
            insets,
            density,
            navigateAction
        )
        is Data.Failed -> ArtistsErrorState(artistsData.message)
        Data.Empty -> ArtistsEmptyState()
        Data.Loading -> ArtistsLoadingState()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistsList(
    artists: List<Artist>,
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

@Composable
fun ArtistsErrorState(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(64.dp)
    )
}

@Composable
fun ArtistsEmptyState() {
    Text(
        text = "Empty",
        modifier = Modifier.padding(64.dp)
    )
}

@Composable
fun ArtistsLoadingState() {
    Text(
        text = "Loading",
        modifier = Modifier.padding(64.dp)
    )
}

    