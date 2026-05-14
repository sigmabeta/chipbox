package net.sigmabeta.chipbox.features.nowplaying.real

import net.sigmabeta.sage.images.SourceInfo

data class NowPlayingModel(
    val artwork: SourceInfo,
    val sessionTypeLabel: String,
    val sessionSourceName: String,
    val title: String,
    val artistsCaption: String,
    val gameTitle: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val lengthMs: Long,
    val canSkipForward: Boolean,
    val isShuffled: Boolean,
    val repeatMode: RepeatMode,
) {
    companion object {
        val Empty = NowPlayingModel(
            artwork = SourceInfo(info = null),
            sessionTypeLabel = "",
            sessionSourceName = "",
            title = "",
            artistsCaption = "",
            gameTitle = "",
            isPlaying = false,
            positionMs = 0L,
            lengthMs = 0L,
            canSkipForward = false,
            isShuffled = false,
            repeatMode = RepeatMode.OFF,
        )
    }
}
