package net.sigmabeta.chipbox.features.rescanstatus

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class RescanStatusAction : ChipboxAction() {
    /** Tapping an added/updated game row opens its detail screen. */
    data class GameClicked(val gameId: Long) : RescanStatusAction()
}
