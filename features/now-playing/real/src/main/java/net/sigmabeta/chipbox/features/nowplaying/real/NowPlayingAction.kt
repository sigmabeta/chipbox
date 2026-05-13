package net.sigmabeta.chipbox.features.nowplaying.real

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class NowPlayingAction : ChipboxAction() {
    data object PlayPauseClicked : NowPlayingAction()
    data object SkipForwardClicked : NowPlayingAction()
    data object SkipBackClicked : NowPlayingAction()
    data class SeekRequested(val positionMs: Long) : NowPlayingAction()
}
