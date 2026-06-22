package net.sigmabeta.chipbox.features.playlistdetail.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.playlistdetail.PlaylistDetailState
import net.sigmabeta.chipbox.models.Playlist
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.fake.FakeModelGenerator
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.list.WidthClass

@DevicePreviews
@Composable
internal fun PlaylistDetail(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = playlistDetailState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun PlaylistDetailEmpty(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = PlaylistDetailState(
            playlist = LCE.Content(Playlist(id = 1, name = "New Playlist", trackCount = 0, createdAtMs = 1)),
            tracks = LCE.Content(emptyList()),
        ),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun PlaylistDetailEditing(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = playlistDetailState().copy(isEditing = true),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun PlaylistDetailRenaming(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = playlistDetailState().copy(isEditing = true, isRenaming = true),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun PlaylistDetailConfirmingDelete(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = playlistDetailState().copy(isEditing = true, isConfirmingDelete = true),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun PlaylistDetailNotFound(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = PlaylistDetailState(notFound = true),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

private fun playlistDetailState(): PlaylistDetailState {
    val generator = FakeModelGenerator()
    val artists = generator.randomArtists()
    val games = generator.randomGames()
    val tracks = generator.randomTracks(artists, games)
    return PlaylistDetailState(
        playlist = LCE.Content(Playlist(id = 1, name = "Boss Themes", trackCount = tracks.size, createdAtMs = 1)),
        tracks = LCE.Content(tracks),
    )
}
