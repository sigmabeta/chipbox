package net.sigmabeta.chipbox.features.nowplaying.real

import net.sigmabeta.chipbox.appcomm.ChipboxAction

sealed class NowPlayingAction : ChipboxAction() {
    data object PlayPauseClicked : NowPlayingAction()
    data object SkipForwardClicked : NowPlayingAction()
    data object SkipBackClicked : NowPlayingAction()

    /** Toggle shuffle. Now fired only from the CONTROLS context menu row, not a transport button. */
    data object ShuffleClicked : NowPlayingAction()

    /** Cycle the repeat mode. Now fired only from the CONTROLS context menu row. */
    data object RepeatClicked : NowPlayingAction()

    data object BackClicked : NowPlayingAction()
    data object PlayerSettingsClicked : NowPlayingAction()
    data class SeekRequested(val positionMs: Long) : NowPlayingAction()
    data class DismissErrorClicked(val id: Long) : NowPlayingAction()

    /** Tap on the track-info block — opens the LINKS context menu. */
    data object TrackInfoClicked : NowPlayingAction()

    /** Transport "menu" button (where repeat used to live) — opens the CONTROLS context menu. */
    data object MenuClicked : NowPlayingAction()

    /** Transport "setlist" button — toggles the inline SETLIST mode (the reorderable queue). */
    data object SetlistClicked : NowPlayingAction()

    /** A row in the inline setlist was tapped — jump playback to that track. */
    data class SetlistTrackClicked(val trackId: Long) : NowPlayingAction()

    /** A setlist row was swiped away — remove that track from the queue. */
    data class SetlistTrackRemoved(val trackId: Long) : NowPlayingAction()

    /** The context menu's back row — return to the NONE state (track info). */
    data object ContextMenuBackClicked : NowPlayingAction()

    /** LINKS game row — navigate to the playing track's game detail screen. */
    data object ContextMenuGameClicked : NowPlayingAction()

    /**
     * LINKS artist row — navigate to artist detail when the track has a single artist, or expand
     * into the ARTISTS state to pick one when it has several. The VM decides which.
     */
    data object ContextMenuArtistsClicked : NowPlayingAction()

    /** ARTISTS row — navigate to the detail screen for one specific artist. */
    data class ContextMenuArtistClicked(val artistId: Long) : NowPlayingAction()
}
