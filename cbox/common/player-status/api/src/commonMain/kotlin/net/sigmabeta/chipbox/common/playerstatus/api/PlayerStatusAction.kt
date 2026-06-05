package net.sigmabeta.chipbox.common.playerstatus.api

import net.sigmabeta.chipbox.appcomm.ChipboxAction

/**
 * User intents emitted by the mini-player ([PlayerStatus]). Routed through
 * [PlayerStatusViewModel.sendAction] so each one is logged like every other screen's actions.
 */
sealed class PlayerStatusAction : ChipboxAction() {
    /** The card body was tapped — the host navigates to Now Playing; the VM just logs it. */
    data object CardClicked : PlayerStatusAction()

    /** The play/pause transport button was tapped. */
    data object PlayPauseClicked : PlayerStatusAction()
}
