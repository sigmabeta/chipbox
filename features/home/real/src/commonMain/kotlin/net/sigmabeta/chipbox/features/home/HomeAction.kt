package net.sigmabeta.chipbox.features.home

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class HomeAction : ChipboxAction() {
    data class GameClicked(val id: Long) : HomeAction()
    data object RandomSongClicked : HomeAction()
    data object RandomGameClicked : HomeAction()
    data object RandomArtistClicked : HomeAction()
}
