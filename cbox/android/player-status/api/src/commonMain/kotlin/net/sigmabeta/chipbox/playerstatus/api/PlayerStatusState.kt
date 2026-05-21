package net.sigmabeta.chipbox.playerstatus.api

import net.sigmabeta.sage.images.SourceInfo

data class PlayerStatusState(
    val visible: Boolean,
    val isPlaying: Boolean,
    val title: String,
    val artistsCaption: String,
    val artwork: SourceInfo,
) {
    companion object {
        val Empty = PlayerStatusState(
            visible = false,
            isPlaying = false,
            title = "",
            artistsCaption = "",
            artwork = SourceInfo(info = null),
        )
    }
}
