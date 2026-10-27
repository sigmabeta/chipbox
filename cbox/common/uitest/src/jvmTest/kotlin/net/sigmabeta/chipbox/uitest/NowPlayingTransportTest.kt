package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.nowplaying.NowPlaying
import net.sigmabeta.chipbox.features.nowplaying.real.NOW_PLAYING_PLAY_PAUSE_BUTTON_TAG
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.director.SessionRequest
import kotlin.test.Test

/**
 * The Now Playing screen's play/pause transport: addressable by test tag, announced as the action
 * it performs (pause while playing), and wired to the director.
 */
class NowPlayingTransportTest {

    @Test
    fun pauseControlWhilePlayingAnnouncesPauseAndRequestsIt() = runChipboxUiTest {
        val track = libraryTracks().first { it.title == TRACK_TITLE }
        startFromSession(
            session = Session(type = SessionType.ALL_TRACKS, contentId = 0L),
            track = track,
            playbackState = PlayerState.PLAYING,
        )
        startAtScreen(NowPlaying)
        waitForContent(track.title)

        assertContentDescription(NOW_PLAYING_PLAY_PAUSE_BUTTON_TAG, PAUSE_DESCRIPTION)

        clickTag(NOW_PLAYING_PLAY_PAUSE_BUTTON_TAG)

        assertDirectorReceived(SessionRequest.Pause)
    }

    private companion object {
        const val TRACK_TITLE = "Castle 36"

        // The real transport description while playing (strings-accessibility.xml).
        const val PAUSE_DESCRIPTION = "Pause"
    }
}
