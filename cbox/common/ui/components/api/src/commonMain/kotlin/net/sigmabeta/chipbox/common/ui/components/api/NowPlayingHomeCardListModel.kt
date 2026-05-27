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
    // Track playback progress in the unit interval — 0f for "no progress yet" / "unknown
    // length". Only rendered on screens wide enough to comfortably fit a progress bar
    // between the title block and the play/pause button.
    val progressFraction: Float,
    val clickAction: SageAction,
    val playPauseAction: SageAction,
    // Lifecycle actions dispatched as the card enters and leaves composition. Lets the
    // host (HomeViewModel) react to the card being on/off-screen without the renderer
    // having to know what reaction it triggers (e.g. suppressing the bottom mini-player).
    val appearAction: SageAction,
    val disappearAction: SageAction,
) : ListModel() {
    // Single-instance model — there's only ever one "now playing" card on Home at a time.
    override val dataId: Long = 0L
    override val columns: Int = 1
}
