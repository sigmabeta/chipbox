package net.sigmabeta.chipbox.features.nowplaying.real

import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.strings.ChipboxStringId
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.freeform.FreeformState
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.StringProvider

data class NowPlayingState(
    val track: Track? = null,
    val playback: ChipboxPlaybackState? = null,
) : FreeformState<NowPlayingModel>() {

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.NOW_PLAYING_SCREEN_TITLE),
        shouldShowBack = true,
    )

    override fun toContent(stringProvider: StringProvider): NowPlayingModel = NowPlayingModel(
        artwork = SourceInfo(info = track?.game?.photoUrl),
        title = track?.title.orEmpty(),
        artistsCaption = track?.artists?.joinToString(", ") { it.name }.orEmpty(),
        gameTitle = track?.game?.title.orEmpty(),
        isPlaying = playback?.state?.isPlaying() == true,
        positionMs = playback?.position ?: 0L,
        lengthMs = track?.trackLengthMs ?: 0L,
        canSkipForward = playback?.skipForwardAllowed == true,
    )

    override fun errorContent(error: Throwable): NowPlayingModel = NowPlayingModel.Empty

    // Mirrors PlayerStatusViewModel.isPlaying() so the play/pause icon agrees with the mini-bar.
    private fun PlayerState.isPlaying(): Boolean = when (this) {
        PlayerState.PLAYING,
        PlayerState.PRELOADING,
        PlayerState.BUFFERING,
        PlayerState.FAST_FORWARDING,
        PlayerState.REWINDING,
        PlayerState.ENDING -> true
        PlayerState.PAUSED,
        PlayerState.ERROR,
        PlayerState.IDLE,
        PlayerState.STOPPED -> false
    }
}
