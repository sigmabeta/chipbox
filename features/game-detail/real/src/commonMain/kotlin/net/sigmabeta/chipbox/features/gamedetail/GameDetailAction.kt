package net.sigmabeta.chipbox.features.gamedetail

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class GameDetailAction : ChipboxAction() {
    data class TrackClicked(val position: Int) : GameDetailAction()
    data class ArtistClicked(val id: Long) : GameDetailAction()
    data object PlayAllClicked : GameDetailAction()
    data object ShuffleAllClicked : GameDetailAction()
    data object AddToFavoritesClicked : GameDetailAction()
    data object AddToPlaylistClicked : GameDetailAction()
}
