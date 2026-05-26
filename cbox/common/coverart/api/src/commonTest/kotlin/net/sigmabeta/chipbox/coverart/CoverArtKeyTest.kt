package net.sigmabeta.chipbox.coverart

import net.sigmabeta.chipbox.models.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CoverArtKeyTest {

    @Test
    fun `same title and platforms produce the same key`() {
        val a = coverArtKey("Chrono Trigger", setOf(Platform.SNES))
        val b = coverArtKey("Chrono Trigger", setOf(Platform.SNES))
        assertEquals(a, b)
    }

    @Test
    fun `platform set order does not affect the key`() {
        // The key is used as a cache lookup, so a port spanning multiple platforms must hash the
        // same regardless of how the Set's iteration happens to surface them.
        val ordered = coverArtKey("Ico", setOf(Platform.PSX, Platform.PS2))
        val swapped = coverArtKey("Ico", setOf(Platform.PS2, Platform.PSX))
        assertEquals(ordered, swapped)
    }

    @Test
    fun `different platform sets produce different keys`() {
        // Snake-eating "Metal Gear" exists on both NES and PSX with very different cover art —
        // they must not collide in the cache.
        val nes = coverArtKey("Metal Gear", setOf(Platform.NES))
        val psx = coverArtKey("Metal Gear", setOf(Platform.PSX))
        assertNotEquals(nes, psx)
    }

    @Test
    fun `different titles produce different keys`() {
        val a = coverArtKey("Game A", setOf(Platform.SNES))
        val b = coverArtKey("Game B", setOf(Platform.SNES))
        assertNotEquals(a, b)
    }

    @Test
    fun `empty platform set still yields a deterministic key`() {
        // Nothing in the contract forbids an empty platform set; the joiner just produces an
        // empty suffix. Lock the format so a future "smart" change doesn't accidentally drop the
        // separator and collide with the title-only key of a different game.
        assertEquals("Game|", coverArtKey("Game", emptySet()))
    }
}
