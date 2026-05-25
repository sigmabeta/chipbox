package net.sigmabeta.chipbox.features.playbackstatus.real

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class PlaybackStatusAction : ChipboxAction() {
    data object CopyDebugInfoClicked : PlaybackStatusAction()
}
