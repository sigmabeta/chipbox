package net.sigmabeta.chipbox.readers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamedAudioTest {

    @Test
    fun `streamed subsongs are numbered from one`() {
        // vgmstream's subsong argument is 1-based: the native side clamps `subsong > 0 ? subsong : 1`,
        // and every streamed track already in the database has trackNumber >= 1.
        assertEquals(1, subsongTrackNumber(0))
        assertEquals(2, subsongTrackNumber(1))
        assertEquals(239, subsongTrackNumber(238))
    }

    @Test
    fun `the streamed convention is the 1-based exception to the chiptune readers`() {
        // The chiptune readers number 0-based (NSF/GBS/RSN subtune index); streamed readers must not.
        val chiptuneFirstTrack = 0
        assertTrue(subsongTrackNumber(chiptuneFirstTrack) > chiptuneFirstTrack)
    }
}
