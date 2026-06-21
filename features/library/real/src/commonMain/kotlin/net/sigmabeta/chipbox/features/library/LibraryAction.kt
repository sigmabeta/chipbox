package net.sigmabeta.chipbox.features.library

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class LibraryAction : ChipboxAction() {
    data object FavoritesClicked : LibraryAction()
    data object PlaylistsClicked : LibraryAction()
    data object BrowseByGameClicked : LibraryAction()
    data object BrowseByPlatformClicked : LibraryAction()
    data object BrowseByArtistClicked : LibraryAction()
    data object BrowseAllTracksClicked : LibraryAction()
}
