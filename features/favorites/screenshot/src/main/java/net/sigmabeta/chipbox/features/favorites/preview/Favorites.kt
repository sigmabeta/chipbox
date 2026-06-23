package net.sigmabeta.chipbox.features.favorites.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.favorites.FavoritesState
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.fake.FakeModelGenerator
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.list.WidthClass

@DevicePreviews
@Composable
internal fun Favorites(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = favoritesState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun FavoritesLoading(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = favoritesLoadingState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun FavoritesEmpty(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = FavoritesState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

private fun favoritesState(): FavoritesState {
    val generator = FakeModelGenerator()
    val artists = generator.randomArtists()
    val games = generator.randomGames()
    val tracks = generator.randomTracks(artists, games)
    return FavoritesState(
        tracks = LCE.Content(tracks),
        games = LCE.Content(games),
        artists = LCE.Content(artists),
        playingTrackId = tracks.firstOrNull()?.id,
    )
}

private fun favoritesLoadingState(): FavoritesState {
    val name = FakeModelGenerator().loadingName()
    return FavoritesState(
        tracks = LCE.Loading(name),
        games = LCE.Loading(name),
        artists = LCE.Loading(name),
    )
}
