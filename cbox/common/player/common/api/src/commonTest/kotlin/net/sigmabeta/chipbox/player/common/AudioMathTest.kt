package net.sigmabeta.chipbox.player.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit conversions in [Utils.kt] are the lingua franca between the emulator (frames), the buffer
 * layout (samples), and the disk format (bytes). A silent off-by-one in any direction shifts every
 * downstream timestamp and seek by a stereo frame, so the rules are nailed down here.
 */
class AudioMathTest {

    @Test
    fun `framesToMillis at 44100 Hz inverts millisToFrames`() {
        assertEquals(1000.0, 44_100.framesToMillis(44_100))
        assertEquals(44_100, 1000.0.millisToFrames(44_100))
    }

    @Test
    fun `framesToMillis stays in Double space so a single frame keeps sub-millisecond precision`() {
        // sampleRate.rateInMillis() promotes to Double (the MILLIS_PER_SECOND constant is 1000.0),
        // so 1 frame at 44.1 kHz is ~0.0227 ms — not truncated to zero. The Double return type is
        // load-bearing for downstream timestamp math.
        val ms = 1.framesToMillis(44_100)
        assertTrue(ms > 0.022 && ms < 0.023, "expected ~0.0227 ms, got $ms")
    }

    @Test
    fun `stereo frame is two samples and four bytes`() {
        assertEquals(2, SHORTS_PER_FRAME)
        assertEquals(4, BYTES_PER_FRAME)
        assertEquals(2, BYTES_PER_SAMPLE)
    }

    @Test
    fun `framesToSamples and samplesToFrames invert at frame boundaries`() {
        assertEquals(20, 10.framesToSamples())
        assertEquals(10, 20.samplesToFrames())
    }

    @Test
    fun `samplesToBytes and bytesToSamples invert at sample boundaries`() {
        assertEquals(20, 10.samplesToBytes())
        assertEquals(10, 20.bytesToSamples())
    }

    @Test
    fun `samplesToFrames truncates an odd sample count`() {
        // An odd sample count means a half-frame slice — int division drops the lone sample,
        // matching what the buffer code does when it ignores trailing channel data.
        assertEquals(5, 11.samplesToFrames())
    }

    @Test
    fun `isDivisibleBy works for Int and Long`() {
        assertTrue(10.isDivisibleBy(5))
        assertFalse(10.isDivisibleBy(3))
        assertTrue(10L.isDivisibleBy(5))
        assertFalse(10L.isDivisibleBy(3))
    }

    @Test
    fun `clear zeroes every element of a ShortArray`() {
        val buffer = ShortArray(8) { (it * 100).toShort() }
        buffer.clear()
        assertTrue(buffer.all { it == 0.toShort() })
    }

    @Test
    fun `toShortValue truncates the Double down to a Short`() {
        // Cast path is Double -> Int -> Short, so values above Short.MAX_VALUE wrap rather than
        // saturate — locking this in so any future "clamp" change is intentional.
        assertEquals(123.toShort(), 123.7.toShortValue())
        assertEquals((-123).toShort(), (-123.9).toShortValue())
    }
}
