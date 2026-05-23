package net.sigmabeta.chipbox.common.playerstatus.api

import net.sigmabeta.sage.images.SourceInfo

data class PlayerStatusState(
    val visible: Boolean,
    val isPlaying: Boolean,
    /** True while the player is buffering — the play/pause control shows a spinner. */
    val isBuffering: Boolean,
    /** True on a fatal playback error — the play/pause control shows a warning icon. */
    val isError: Boolean,
    val title: String,
    val artistsCaption: String,
    val artwork: SourceInfo,
) {
    companion object {
        val Empty = PlayerStatusState(
            visible = false,
            isPlaying = false,
            isBuffering = false,
            isError = false,
            title = "",
            artistsCaption = "",
            artwork = SourceInfo(info = null),
        )
    }
}
