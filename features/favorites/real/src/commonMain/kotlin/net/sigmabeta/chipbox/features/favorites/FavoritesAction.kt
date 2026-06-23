package net.sigmabeta.chipbox.features.favorites

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class FavoritesAction : ChipboxAction() {
    data class TrackClicked(val position: Int) : FavoritesAction()
    data class GameClicked(val id: Long) : FavoritesAction()
    data class ArtistClicked(val id: Long) : FavoritesAction()
}
