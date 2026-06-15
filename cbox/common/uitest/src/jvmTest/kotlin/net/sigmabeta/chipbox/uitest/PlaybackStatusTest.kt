package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatus
import kotlin.test.Test

/**
 * The debug playback-status screen renders its diagnostic sections (with empty/idle values by
 * default). Pure display — its only action copies debug text to the clipboard (handled outside the
 * harness).
 */
class PlaybackStatusTest {
    @Test
    fun showsDiagnosticSections() = runChipboxUiTest {
        startAtScreen(PlaybackStatus)

        assertSectionHeader("Playback")
    }
}
