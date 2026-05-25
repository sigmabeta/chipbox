package net.sigmabeta.chipbox.features.browsebyartist

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class BrowseByArtistAction : ChipboxAction() {
    data class ArtistClicked(val id: Long) : BrowseByArtistAction()
}
