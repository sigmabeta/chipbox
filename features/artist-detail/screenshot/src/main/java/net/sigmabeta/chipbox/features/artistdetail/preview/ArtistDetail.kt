package net.sigmabeta.chipbox.features.artistdetail.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.artistdetail.ArtistDetailState
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.fake.FakeModelGenerator
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.list.WidthClass

@DevicePreviews
@Composable
internal fun ArtistDetail(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = artistScreenState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun ArtistDetailLoading(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = artistScreenLoadingState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

private fun artistScreenState(): ArtistDetailState {
    val generator = FakeModelGenerator()

    val artists = generator.randomArtists()
    val games = generator.randomGames()
    val tracks = generator.randomTracks(artists, games)

    return ArtistDetailState(
        artist = LCE.Content(artists.first()),
        tracks = LCE.Content(tracks),
        games = LCE.Content(games),
    )
}

private fun artistScreenLoadingState(): ArtistDetailState {
    val name = FakeModelGenerator().loadingName()
    return ArtistDetailState(
        artist = LCE.Loading(name),
        tracks = LCE.Loading(name),
        games = LCE.Loading(name),
    )
}
