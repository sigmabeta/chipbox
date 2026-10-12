package net.sigmabeta.chipbox.features.tracksforartist.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.tracksforartist.TracksForArtistState
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.fake.FakeModelGenerator
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.list.WidthClass

@DevicePreviews
@Composable
internal fun TracksForArtist(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = tracksForArtistState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun TracksForArtistLoading(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = tracksForArtistLoadingState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

private fun tracksForArtistState(): TracksForArtistState {
    val generator = FakeModelGenerator()
    val artists = generator.randomArtists()
    val games = generator.randomGames()
    val tracks = generator.randomTracks(artists, games)
    return TracksForArtistState(
        artistName = artists.first().name,
        tracks = LCE.Content(tracks),
    )
}

private fun tracksForArtistLoadingState(): TracksForArtistState {
    val name = FakeModelGenerator().loadingName()
    return TracksForArtistState(
        artistName = "Loading Artist",
        tracks = LCE.Loading(name),
    )
}
