package net.sigmabeta.chipbox.features.browsealltracks

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class BrowseAllTracksAction : ChipboxAction() {
    data class TrackClicked(val position: Int) : BrowseAllTracksAction()
    data object ShuffleAllClicked : BrowseAllTracksAction()
}
