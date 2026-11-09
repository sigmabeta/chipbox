package net.sigmabeta.chipbox.features.gamesforplatform

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class GamesForPlatformAction : ChipboxAction() {
    data object PlayAllClicked : GamesForPlatformAction()
    data object ShuffleAllClicked : GamesForPlatformAction()
    data class GameClicked(val id: Long) : GamesForPlatformAction()
}
