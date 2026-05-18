package net.sigmabeta.chipbox.features.browsealltracks.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracksState
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.fake.FakeModelGenerator
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.list.WidthClass

@DevicePreviews
@Composable
internal fun BrowseAllTracks(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = browseAllTracksState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun BrowseAllTracksLoading(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = browseAllTracksLoadingState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

private fun browseAllTracksState(): BrowseAllTracksState {
    val generator = FakeModelGenerator()
    val artists = generator.randomArtists()
    val games = generator.randomGames()
    val tracks = generator.randomTracks(artists, games)
    return BrowseAllTracksState(
        tracks = LCE.Content(tracks),
        playingTrackId = tracks.first().id,
    )
}

private fun browseAllTracksLoadingState(): BrowseAllTracksState {
    val name = FakeModelGenerator().loadingName()
    return BrowseAllTracksState(tracks = LCE.Loading(name))
}
