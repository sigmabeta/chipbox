package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.browsealltracks.BrowseAllTracks
import net.sigmabeta.chipbox.player.director.SessionRequest
import kotlin.test.Test

/**
 * Browse-all-tracks shows a "shuffle all" CTA and every track; both the CTA and a track row start a
 * session through the Director.
 */
class BrowseAllTracksTest {
    @Test
    fun showsShuffleCtaAndTracks() = runChipboxUiTest {
        startAtScreen(BrowseAllTracks)

        assertCtaDisplayed("Shuffle all tracks")
    }

    @Test
    fun shuffleAllStartsASession() = runChipboxUiTest {
        startAtScreen(BrowseAllTracks)

        click("Shuffle all tracks")

        assertDirectorReceived<SessionRequest.Start>()
    }

    @Test
    fun tappingTrackStartsASession() = runChipboxUiTest {
        startAtScreen(BrowseAllTracks)

        // "Battle 19" is the first track row for seed 1234 (tracks are sorted by title).
        clickNameCaptionValueItem("Battle 19")

        assertDirectorReceived<SessionRequest.Start>()
    }
}
