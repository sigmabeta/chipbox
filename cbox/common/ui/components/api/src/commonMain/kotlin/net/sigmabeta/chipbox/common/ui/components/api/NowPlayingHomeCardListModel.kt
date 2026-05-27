package net.sigmabeta.chipbox.common.ui.components.api

import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.images.SourceInfo

/**
 * Larger, single-item variant of the bottom-of-screen mini-player intended for use as a Home
 * row. Carries the same data fields as the mini-player's `PlayerStatusState` but lives here
 * (rather than reusing that type) because `cbox.common.player-status.api` is downstream of
 * this module and we'd otherwise create a cycle.
 *
 * Has two separate click actions: tapping the card opens the now-playing screen; tapping the
 * embedded play/pause button toggles playback in place.
 */
data class NowPlayingHomeCardListModel(
    val title: String,
    val artistsCaption: String,
    val artwork: SourceInfo,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val isError: Boolean,
    val clickAction: SageAction,
    val playPauseAction: SageAction,
) : ListModel() {
    // Single-instance model — there's only ever one "now playing" card on Home at a time.
    override val dataId: Long = 0L
    override val columns: Int = 1
}
