package net.sigmabeta.chipbox.player.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PcmCacheKeyTest {

    @Test
    fun `filename concatenates the three identity fields`() {
        val key = PcmCacheKey(sourceHash = "abc123", trackNumber = 4, sampleRate = 44_100)
        assertEquals("abc123-4-44100.pcm", key.filename())
    }

    @Test
    fun `tempFilename appends a tmp suffix to the final filename`() {
        // The .tmp suffix is what the renamer looks for when promoting a finished render — losing
        // the .pcm prefix in front of it would break that pattern.
        val key = PcmCacheKey(sourceHash = "abc123", trackNumber = 0, sampleRate = 48_000)
        assertEquals("abc123-0-48000.pcm.tmp", key.tempFilename())
    }

    @Test
    fun `track number is part of the key`() {
        val a = PcmCacheKey("hash", trackNumber = 0, sampleRate = 44_100)
        val b = PcmCacheKey("hash", trackNumber = 1, sampleRate = 44_100)
        assertNotEquals(a, b)
        assertNotEquals(a.filename(), b.filename())
    }

    @Test
    fun `sample rate is part of the key`() {
        // A rate change must invalidate cache hits — otherwise an emulator update that shifts
        // output rate would replay stale bytes.
        val a = PcmCacheKey("hash", trackNumber = 0, sampleRate = 44_100)
        val b = PcmCacheKey("hash", trackNumber = 0, sampleRate = 48_000)
        assertNotEquals(a, b)
        assertNotEquals(a.filename(), b.filename())
    }
}
