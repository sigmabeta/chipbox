package net.sigmabeta.chipbox.features.gamedetail.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.gamedetail.GameDetailState
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.fake.FakeModelGenerator
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.list.WidthClass

@DevicePreviews
@Composable
internal fun GameDetail(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = gameScreenState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun GameDetailLoading(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = gameScreenLoadingState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

private fun gameScreenState(): GameDetailState {
    val generator = FakeModelGenerator()

    val artists = generator.randomArtists()
    val tracks = generator.randomTracks(artists)
    val game = generator.randomGame(artists, tracks)

    return GameDetailState(
        game = LCE.Content(game),
        tracks = LCE.Content(tracks),
        artists = LCE.Content(artists),
    )
}

private fun gameScreenLoadingState(): GameDetailState {
    val name = FakeModelGenerator().loadingName()
    return GameDetailState(
        game = LCE.Loading(name),
        tracks = LCE.Loading(name),
        artists = LCE.Loading(name),
    )
}
