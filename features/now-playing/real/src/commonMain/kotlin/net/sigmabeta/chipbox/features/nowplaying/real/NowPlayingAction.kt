package net.sigmabeta.chipbox.features.nowplaying.real

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class NowPlayingAction : ChipboxAction() {
    data object PlayPauseClicked : NowPlayingAction()
    data object SkipForwardClicked : NowPlayingAction()
    data object SkipBackClicked : NowPlayingAction()
    data object ShuffleClicked : NowPlayingAction()
    data object RepeatClicked : NowPlayingAction()
    data object BackClicked : NowPlayingAction()
    data object PlayerSettingsClicked : NowPlayingAction()
    data class SeekRequested(val positionMs: Long) : NowPlayingAction()
    data class DismissErrorClicked(val id: Long) : NowPlayingAction()
}
