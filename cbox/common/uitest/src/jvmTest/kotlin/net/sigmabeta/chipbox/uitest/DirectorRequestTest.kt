package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.player.director.SessionRequest
import kotlin.test.Test

/**
 * Phase 4: assert what the playback Director received. Clicking a song on a game starts a session,
 * so the Director gets a [SessionRequest.Start]. Uses a known game/song from the populated library
 * (no seeding); the song row's type doesn't matter, so it's clicked by title.
 */
class DirectorRequestTest {
    @Test
    fun clickingASongStartsPlayback() = runChipboxUiTest {
        startAtScreen(GameDetail(gameId("Iron Quest")))

        // "Castle 36" is one of Iron Quest's tracks.
        click("Castle 36")

        assertDirectorReceived<SessionRequest.Start>()
    }
}
