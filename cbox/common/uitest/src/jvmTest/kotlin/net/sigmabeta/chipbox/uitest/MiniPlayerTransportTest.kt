package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.library.Library
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.director.SessionRequest
import kotlin.test.Test

/**
 * The bottom mini-player's transport. After a fatal playback error the control shows a warning
 * icon, but tapping it still asks the director to play (the ticket-16 recovery path) — the same
 * intent the browser matrix exercised by hand before the control was addressable.
 */
class MiniPlayerTransportTest {

    @Test
    fun playAfterFatalErrorAsksDirectorToPlay() = runChipboxUiTest {
        // Start on Library, not Home: Home's own now-playing card deliberately hides the
        // mini-player while the card is on screen.
        startAtScreen(Library)
        val track = libraryTracks().first { it.title == TRACK_TITLE }
        startFromSession(
            session = Session(type = SessionType.ALL_TRACKS, contentId = 0L),
            track = track,
            playbackState = PlayerState.ERROR,
        )
        waitForContent(track.title)

        // In ERROR the control shows a warning icon, but its action is still "play" (recover) —
        // which is the intent assistive tech should announce.
        assertContentDescription(MINI_PLAYER_PLAY_PAUSE_TAG, PLAY_DESCRIPTION)

        clickTag(MINI_PLAYER_PLAY_PAUSE_TAG)

        assertDirectorReceived(SessionRequest.Play)
    }

    @Test
    fun pauseDescriptionWhilePlaying() = runChipboxUiTest {
        startAtScreen(Library)
        val track = libraryTracks().first { it.title == TRACK_TITLE }
        startFromSession(
            session = Session(type = SessionType.ALL_TRACKS, contentId = 0L),
            track = track,
            playbackState = PlayerState.PLAYING,
        )
        waitForContent(track.title)

        // Playing, so the control's action is "pause" — the description tracks the action, not
        // the track title showing in the bar.
        assertContentDescription(MINI_PLAYER_PLAY_PAUSE_TAG, PAUSE_DESCRIPTION)
    }

    private companion object {
        const val TRACK_TITLE = "Castle 36"

        // The real mini-player transport descriptions (strings-accessibility.xml).
        const val PLAY_DESCRIPTION = "Play"
        const val PAUSE_DESCRIPTION = "Pause"

        // Kept in sync with PLAYER_STATUS_PLAY_PAUSE_TAG in the player-status module.
        const val MINI_PLAYER_PLAY_PAUSE_TAG = "PlayerStatusPlayPause"
    }
}
