package net.sigmabeta.chipbox.features.artistdetail

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class ArtistDetailAction : ChipboxAction() {
    data class TrackClicked(val position: Int) : ArtistDetailAction()
    data class GameClicked(val id: Long) : ArtistDetailAction()
    data object PlayAllClicked : ArtistDetailAction()
    data object ShuffleAllClicked : ArtistDetailAction()
}
