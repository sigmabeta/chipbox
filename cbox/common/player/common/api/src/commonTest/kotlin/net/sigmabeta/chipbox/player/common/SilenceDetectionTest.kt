package net.sigmabeta.chipbox.player.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SilenceDetectionTest {

    @Test
    fun `silent buffer is reported as silent`() {
        val frames = 4
        val buffer = ShortArray(frames * SHORTS_PER_FRAME)
        assertTrue(isBufferSilent(buffer, frames))
    }

    @Test
    fun `samples within the silence threshold count as silent`() {
        // SILENCE_THRESHOLD_AMPLITUDE = 16; both ±16 should still register as silent — the
        // tolerance is intentionally inclusive so a DC offset of -1 doesn't trip detection.
        val frames = 2
        val buffer = ShortArray(frames * SHORTS_PER_FRAME) { (if (it.isEven()) 16 else -16).toShort() }
        assertTrue(isBufferSilent(buffer, frames))
    }

    @Test
    fun `sample above the threshold breaks silence`() {
        val frames = 2
        val buffer = ShortArray(frames * SHORTS_PER_FRAME)
        buffer[3] = 17 // one sample louder than SILENCE_THRESHOLD_AMPLITUDE
        assertFalse(isBufferSilent(buffer, frames))
    }

    @Test
    fun `negative sample above threshold also breaks silence`() {
        val frames = 2
        val buffer = ShortArray(frames * SHORTS_PER_FRAME)
        buffer[0] = -17
        assertFalse(isBufferSilent(buffer, frames))
    }

    @Test
    fun `frame count zero returns false`() {
        // Documented: "no audio to judge" yields false rather than true — callers use this as a
        // "should I drop this buffer" gate.
        assertFalse(isBufferSilent(ShortArray(8), frameCount = 0))
        assertFalse(isBufferSilent(ShortArray(8), frameCount = -1))
    }

    @Test
    fun `audible samples past frameCount are ignored`() {
        val frames = 2
        val buffer = ShortArray(frames * SHORTS_PER_FRAME)
        buffer[frames * SHORTS_PER_FRAME - 1] = 0
        // Stuff a loud sample *after* the inspected window — must not flip the verdict.
        val padded = buffer + shortArrayOf(30_000, 30_000)
        assertTrue(isBufferSilent(padded, frames))
    }

    @Test
    fun `firstAudibleFrame returns the frame index of the first loud sample`() {
        val frames = 4
        val buffer = ShortArray(frames * SHORTS_PER_FRAME)
        // Frame 2, right channel = absolute sample index 2*2+1 = 5.
        buffer[5] = 200
        assertEquals(2, firstAudibleFrame(buffer, frames))
    }

    @Test
    fun `firstAudibleFrame returns 0 when the first frame is already audible`() {
        val frames = 4
        val buffer = ShortArray(frames * SHORTS_PER_FRAME)
        buffer[0] = 1_000
        assertEquals(0, firstAudibleFrame(buffer, frames))
    }

    @Test
    fun `firstAudibleFrame returns -1 for fully silent input`() {
        assertEquals(-1, firstAudibleFrame(ShortArray(8), frameCount = 4))
        assertEquals(-1, firstAudibleFrame(ShortArray(8), frameCount = 0))
        // Negative frameCount should not cause an out-of-bounds — the coerceAtLeast(0) guards it.
        assertEquals(-1, firstAudibleFrame(ShortArray(8), frameCount = -5))
    }

    @Test
    fun `maxAmplitude returns zero for empty or silent input`() {
        assertEquals(0, maxAmplitude(ShortArray(8), frameCount = 0))
        assertEquals(0, maxAmplitude(ShortArray(8), frameCount = -3))
        assertEquals(0, maxAmplitude(ShortArray(8), frameCount = 4))
    }

    @Test
    fun `maxAmplitude returns the largest absolute sample`() {
        val frames = 4
        val buffer = ShortArray(frames * SHORTS_PER_FRAME)
        buffer[2] = 500
        buffer[5] = -1_200
        buffer[7] = 300
        assertEquals(1_200, maxAmplitude(buffer, frames))
    }

    @Test
    fun `maxAmplitude handles Short MIN_VALUE without overflow`() {
        // abs(Short.MIN_VALUE.toInt()) is 32768 — would overflow if we stayed in Short space.
        // The function returns Int specifically to avoid this; lock that contract in.
        val frames = 2
        val buffer = ShortArray(frames * SHORTS_PER_FRAME)
        buffer[1] = Short.MIN_VALUE
        assertEquals(32_768, maxAmplitude(buffer, frames))
    }

    private fun Int.isEven() = this and 1 == 0
}
