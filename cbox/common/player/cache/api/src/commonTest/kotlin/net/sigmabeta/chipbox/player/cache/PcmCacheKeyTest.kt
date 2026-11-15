package net.sigmabeta.chipbox.player.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PcmCacheKeyTest {

    @Test
    fun `filename concatenates the three identity fields`() {
        val key = PcmCacheKey(sourceHash = "abc123", trackNumber = 4, renderVersion = 2)
        assertEquals("abc123-4-r2.pcm", key.filename())
    }

    @Test
    fun `tempFilename appends a tmp suffix to the final filename`() {
        // The .tmp suffix is what the renamer looks for when promoting a finished render — losing
        // the .pcm prefix in front of it would break that pattern.
        val key = PcmCacheKey(sourceHash = "abc123", trackNumber = 0, renderVersion = 1)
        assertEquals("abc123-0-r1.pcm.tmp", key.tempFilename())
    }

    @Test
    fun `track number is part of the key`() {
        val a = PcmCacheKey("hash", trackNumber = 0, renderVersion = 1)
        val b = PcmCacheKey("hash", trackNumber = 1, renderVersion = 1)
        assertNotEquals(a, b)
        assertNotEquals(a.filename(), b.filename())
    }

    @Test
    fun `render version is part of the key`() {
        // Bumping a backend's render version must invalidate its cache hits — otherwise an
        // emulator change that alters its output would replay stale bytes.
        val a = PcmCacheKey("hash", trackNumber = 0, renderVersion = 1)
        val b = PcmCacheKey("hash", trackNumber = 0, renderVersion = 2)
        assertNotEquals(a, b)
        assertNotEquals(a.filename(), b.filename())
    }
}
