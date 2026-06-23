package net.sigmabeta.chipbox.features.playlists.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.features.playlists.PlaylistsState
import net.sigmabeta.chipbox.models.Playlist
import net.sigmabeta.chipbox.ui.previews.DevicePreviews
import net.sigmabeta.chipbox.ui.previews.ListScreenPreview
import net.sigmabeta.chipbox.ui.previews.previewWidthClass
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.list.WidthClass

@DevicePreviews
@Composable
internal fun Playlists(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = playlistsState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun PlaylistsEmpty(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = PlaylistsState(),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

@DevicePreviews
@Composable
internal fun PlaylistsPicker(
    darkTheme: Boolean = isSystemInDarkTheme(),
    syntheticWidthClass: WidthClass = previewWidthClass(),
) {
    ListScreenPreview(
        screenState = playlistsState().copy(isPicker = true),
        syntheticWidthClass = syntheticWidthClass,
        darkTheme = darkTheme,
    )
}

private fun playlistsState(): PlaylistsState = PlaylistsState(
    playlists = LCE.Content(
        listOf(
            Playlist(id = 1, name = "Boss Themes", trackCount = 14, createdAtMs = 3),
            Playlist(id = 2, name = "Late Night Coding", trackCount = 42, createdAtMs = 2),
            Playlist(id = 3, name = "Single Track", trackCount = 1, createdAtMs = 1),
        ),
    ),
)
