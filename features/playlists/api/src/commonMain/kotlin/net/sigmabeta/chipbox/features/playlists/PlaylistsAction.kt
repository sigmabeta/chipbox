package net.sigmabeta.chipbox.features.playlists

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class PlaylistsAction : ChipboxAction() {
    data class PlaylistClicked(val id: Long) : PlaylistsAction()
    data object NewPlaylistClicked : PlaylistsAction()
}
