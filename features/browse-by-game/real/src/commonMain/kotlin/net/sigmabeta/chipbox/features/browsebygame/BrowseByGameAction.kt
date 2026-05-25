package net.sigmabeta.chipbox.features.browsebygame

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class BrowseByGameAction : ChipboxAction() {
    data class GameClicked(val id: Long) : BrowseByGameAction()
}
