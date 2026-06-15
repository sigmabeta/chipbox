package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.gamedetail.GameDetail
import net.sigmabeta.chipbox.player.director.SessionRequest
import kotlin.test.Test

/**
 * Phase 4: assert what the playback Director received. Clicking a song on a game starts a session,
 * so the Director gets a [SessionRequest.Start]. Uses a game from the populated library (no
 * seeding); the song row's type doesn't matter, so it's clicked by title.
 */
class DirectorRequestTest {
    @Test
    fun clickingASongStartsPlayback() = runChipboxUiTest {
        val game = firstGame()
        val song = game.tracks.orEmpty().first()
        startAtScreen(GameDetail(game.id))

        click(song.title)

        assertDirectorReceived<SessionRequest.Start>()
    }
}
