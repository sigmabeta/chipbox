package net.sigmabeta.chipbox.features.home

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class HomeAction : ChipboxAction() {
    data class GameClicked(val id: Long) : HomeAction()
    data class ArtistClicked(val id: Long) : HomeAction()
    data class SongClicked(val id: Long) : HomeAction()
    data object RandomSongClicked : HomeAction()
    data object RandomGameClicked : HomeAction()
    data object RandomArtistClicked : HomeAction()
    data object AddFolderClicked : HomeAction()
    data object RescanLibraryClicked : HomeAction()
    data object NowPlayingCardClicked : HomeAction()
    data object NowPlayingPlayPauseClicked : HomeAction()
    data object NowPlayingCardAppeared : HomeAction()
    data object NowPlayingCardDisappeared : HomeAction()
}
