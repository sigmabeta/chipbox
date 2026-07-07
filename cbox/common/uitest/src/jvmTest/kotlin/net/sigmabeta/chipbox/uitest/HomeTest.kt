package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.player.director.SessionRequest
import kotlin.test.Test

/**
 * Drives the Home screen — the shell's default tab, so no `startAtScreen` is needed. Over the
 * harness's default fakes (deterministic library, empty playback history, no live session) the
 * reliably-rendered rows are the date-seeded "Game of the day" hero and the static "RNG Take the
 * Wheel" cards; the history-driven rows (recently/most played) and the Now Playing hero stay
 * hidden because nothing has been played.
 *
 * Content is asserted via `assertSectionHeader` (which scrolls the row into view) and the first
 * RNG card; actions go through the first card of the RNG scroller via `clickFirstCardInHomeSection`
 * — clicking a later card by name is avoided since it can be scrolled off a narrow device's
 * viewport (see docs/architecture/ui-tests-for-agents.md).
 */
class HomeTest {
    @Test
    fun showsGameOfTheDayAndRngSections() = runChipboxUiTest {
        assertTitle("Chipbox")
        assertSectionHeader("Game of the day")
        assertSectionHeader("RNG Take the Wheel")
    }

    @Test
    fun showsTheRandomSongCard() = runChipboxUiTest {
        assertSectionHeader("RNG Take the Wheel")
        assertGridImageItemDisplayed("Random Song")
    }

    @Test
    fun tappingRandomSongCardStartsPlayback() = runChipboxUiTest {
        // Scroll the bottom RNG row into view, then click its first card (Random Song), which
        // asks the director to start a single-track session built from a random library track.
        assertSectionHeader("RNG Take the Wheel")
        clickFirstCardInHomeSection("RNG Take the Wheel")
        assertDirectorReceived<SessionRequest.Start>()
    }
}
