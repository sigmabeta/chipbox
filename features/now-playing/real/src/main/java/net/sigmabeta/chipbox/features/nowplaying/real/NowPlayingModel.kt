package net.sigmabeta.chipbox.features.nowplaying.real

import net.sigmabeta.sage.images.SourceInfo

data class NowPlayingModel(
    val artwork: SourceInfo,
    val title: String,
    val artistsCaption: String,
    val gameTitle: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val lengthMs: Long,
    val canSkipForward: Boolean,
) {
    companion object {
        val Empty = NowPlayingModel(
            artwork = SourceInfo(info = null),
            title = "",
            artistsCaption = "",
            gameTitle = "",
            isPlaying = false,
            positionMs = 0L,
            lengthMs = 0L,
            canSkipForward = false,
        )
    }
}
