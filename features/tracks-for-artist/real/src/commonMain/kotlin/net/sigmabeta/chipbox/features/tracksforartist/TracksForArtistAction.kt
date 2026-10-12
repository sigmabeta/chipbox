package net.sigmabeta.chipbox.features.tracksforartist

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class TracksForArtistAction : ChipboxAction() {
    data object PlayAllClicked : TracksForArtistAction()
    data object ShuffleAllClicked : TracksForArtistAction()
    data class TrackClicked(val position: Int) : TracksForArtistAction()
}
