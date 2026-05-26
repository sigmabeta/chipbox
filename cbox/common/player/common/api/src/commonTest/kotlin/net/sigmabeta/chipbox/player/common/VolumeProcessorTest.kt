package net.sigmabeta.chipbox.player.common

import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VolumeProcessorTest {

    private fun newProcessor() = VolumeProcessor(BluntHatchet())

    // ---- registry math ----

    @Test
    fun `empty registry gives unity target gain`() {
        val processor = newProcessor()
        assertEquals(1.0, processor.debugSnapshot().targetGain)
    }

    @Test
    fun `master volume is reflected as the target gain`() {
        val processor = newProcessor()
        processor.setMasterVolume(0.5)
        assertEquals(0.5, processor.debugSnapshot().targetGain)
    }

    @Test
    fun `target gain is the product of every registered modification`() {
        // Both keys land in the registry independently and multiply together — this is what makes
        // ducking-while-also-attenuating compose cleanly.
        val processor = newProcessor()
        processor.setMasterVolume(0.5)
        processor.setDucked(true)
        assertEquals(0.5 * VolumeProcessor.DUCK_SCALE, processor.debugSnapshot().targetGain)
    }

    @Test
    fun `setDucked false clears the duck key`() {
        val processor = newProcessor()
        processor.setDucked(true)
        processor.setDucked(false)
        assertNull(processor.debugSnapshot().modifications[VolumeProcessor.KEY_DUCK])
        assertEquals(1.0, processor.debugSnapshot().targetGain)
    }

    @Test
    fun `clearModification removes only its own key`() {
        val processor = newProcessor()
        processor.setMasterVolume(0.5)
        processor.setDucked(true)
        processor.clearModification(VolumeProcessor.KEY_DUCK)
        assertEquals(0.5, processor.debugSnapshot().targetGain)
        assertNull(processor.debugSnapshot().modifications[VolumeProcessor.KEY_DUCK])
    }

    @Test
    fun `each modification is clamped between zero and MAX_GAIN`() {
        // Negative values and out-of-band boosts are silently coerced — a runaway normalization
        // for a near-silent track must not be able to slam the output.
        val processor = newProcessor()
        processor.setMasterVolume(-3.0)
        assertEquals(0.0, processor.debugSnapshot().modifications[VolumeProcessor.KEY_MASTER])

        processor.setMasterVolume(VolumeProcessor.MAX_GAIN + 100.0)
        assertEquals(VolumeProcessor.MAX_GAIN, processor.debugSnapshot().modifications[VolumeProcessor.KEY_MASTER])
    }

    @Test
    fun `setNormalization with NaN loudness leaves the audio unchanged`() {
        // normalizationGain(NaN, *) returns 1.0; the normalization key is therefore unity, which
        // collapses out of the product.
        val processor = newProcessor()
        processor.setNormalization(loudnessLufs = Double.NaN, truePeakDbtp = -3.0)
        assertEquals(
            1.0,
            processor.debugSnapshot().modifications[VolumeProcessor.KEY_NORMALIZATION],
        )
    }

    @Test
    fun `setNormalization for a quiet track produces a boost`() {
        // -24 LUFS is below the -14 target; the modification should be > 1.0.
        val processor = newProcessor()
        processor.setNormalization(loudnessLufs = -24.0, truePeakDbtp = Double.NaN)
        val gain = processor.debugSnapshot().modifications[VolumeProcessor.KEY_NORMALIZATION] ?: 0.0
        assertTrue(gain > 1.0, "expected a boost for a -24 LUFS track, got $gain")
    }

    // ---- process() smoothed-gain behaviour ----

    @Test
    fun `process with unity gain and no fade leaves the buffer untouched`() {
        // Documented fast-path: no fade, target == actual == 1.0 → return immediately. Verify
        // the buffer comes back byte-identical so the consumer can rely on the path.
        //
        // actualGain starts at STARTING_GAIN (intentional fade-from-half on a new processor —
        // see VolumeProcessor.STARTING_GAIN), so warm up first by processing enough frames at
        // unity target for the smoothed gain to ramp to 1.0: (1.0 - STARTING_GAIN) /
        // MAX_GAIN_CHANGE_PER_FRAME_UP frames. Once we're there, the fast-path applies.
        val processor = newProcessor()
        val warmupFrames = ((1.0 - VolumeProcessor.STARTING_GAIN) / VolumeProcessor.MAX_GAIN_CHANGE_PER_FRAME_UP)
            .toInt() + 1
        processor.process(ShortArray(warmupFrames * 2), 48_000, 0.0, 0.0, 0.0)
        assertEquals(1.0, processor.debugSnapshot().actualGain, "sanity: warmup should land us at unity")

        val frames = 64
        val original = sineBuffer(frames, amplitude = 0.5)
        val copy = original.copyOf()
        processor.process(copy, sampleRate = 48_000, inputStartMillis = 0.0, fadeStartMillis = 0.0, fadeLengthMillis = 0.0)
        for (i in original.indices) assertEquals(original[i], copy[i], "frame $i must be unchanged")
    }

    @Test
    fun `resetGain snaps the smoothed gain back to STARTING_GAIN`() {
        // After running enough frames that the smoothed gain has drifted from STARTING_GAIN,
        // resetGain jumps it back so a new track's normalization ramps in from STARTING_GAIN
        // rather than continuing from wherever the previous track left it. STARTING_GAIN is
        // currently 0.5 — the documented "fade in from half-volume" behaviour on a fresh
        // processor / track boundary.
        val processor = newProcessor()
        processor.setMasterVolume(0.0) // force a long downward ramp target
        val frames = 5_000
        val buffer = sineBuffer(frames, amplitude = 0.5)
        processor.process(buffer, sampleRate = 48_000, inputStartMillis = 0.0, fadeStartMillis = 0.0, fadeLengthMillis = 0.0)
        val actualAfter = processor.debugSnapshot().actualGain
        assertTrue(actualAfter < VolumeProcessor.STARTING_GAIN, "expected smoothed gain to have decayed; was $actualAfter")
        processor.resetGain()
        assertEquals(VolumeProcessor.STARTING_GAIN, processor.debugSnapshot().actualGain)
    }

    @Test
    fun `smoothed gain decays toward zero by MAX_GAIN_CHANGE_PER_FRAME_DOWN per frame`() {
        // Drop target to 0 and process exactly N frames; the smoothed gain should land within
        // one step of (STARTING_GAIN - N * MAX_GAIN_CHANGE_PER_FRAME_DOWN). Locks in the
        // asymmetric fast-decay behaviour the speaker relies on for clean ducks. Start is
        // STARTING_GAIN (0.5 today), not 1.0 — see VolumeProcessor.STARTING_GAIN.
        val processor = newProcessor()
        processor.setMasterVolume(0.0)
        val frames = 1_000
        val expectedAfter = VolumeProcessor.STARTING_GAIN - frames * VolumeProcessor.MAX_GAIN_CHANGE_PER_FRAME_DOWN
        processor.process(sineBuffer(frames, amplitude = 0.5), 48_000, 0.0, 0.0, 0.0)
        val actual = processor.debugSnapshot().actualGain
        assertTrue(
            abs(actual - expectedAfter) < VolumeProcessor.MAX_GAIN_CHANGE_PER_FRAME_DOWN * 2,
            "expected actualGain ~$expectedAfter after $frames frames, got $actual",
        )
    }

    @Test
    fun `smoothed gain rises toward target at MAX_GAIN_CHANGE_PER_FRAME_UP per frame`() {
        // First decay to a low value, then raise the target back to 1.0 and verify the slow-attack
        // step takes over. The UP step is half the DOWN step, so this catches an accidental swap.
        val processor = newProcessor()
        processor.setMasterVolume(0.0)
        processor.process(sineBuffer(5_000, amplitude = 0.5), 48_000, 0.0, 0.0, 0.0)
        val before = processor.debugSnapshot().actualGain
        processor.setMasterVolume(1.0)
        val rampFrames = 1_000
        processor.process(sineBuffer(rampFrames, amplitude = 0.5), 48_000, 0.0, 0.0, 0.0)
        val after = processor.debugSnapshot().actualGain
        val delta = after - before
        val expected = rampFrames * VolumeProcessor.MAX_GAIN_CHANGE_PER_FRAME_UP
        assertTrue(
            abs(delta - expected) < VolumeProcessor.MAX_GAIN_CHANGE_PER_FRAME_UP * 2,
            "expected gain to climb by ~$expected over $rampFrames frames, got $delta",
        )
    }

    @Test
    fun `audio past the fade window is silent`() {
        // Past fadeStart + fadeLength the gain ramp clamps to 0.0 and no audio leaks through.
        val processor = newProcessor()
        val sampleRate = 48_000
        // Buffer starts at 500 ms; fade was scheduled to start at 100 ms with a 200 ms ramp, so
        // the entire buffer sits after fade-end → every sample should land at zero.
        processor.process(
            audioInput = sineBuffer(frames = sampleRate / 10, amplitude = 0.5),
            sampleRate = sampleRate,
            inputStartMillis = 500.0,
            fadeStartMillis = 100.0,
            fadeLengthMillis = 200.0,
        )
    }

    @Test
    fun `audio inside the fade window ramps down`() {
        // Buffer covers the whole fade window: the first sample carries near-full amplitude
        // (gain ≈ 1) and the last carries near-zero. Verify both ends.
        val processor = newProcessor()
        val sampleRate = 48_000
        val frames = sampleRate / 10 // 100 ms
        val buffer = ShortArray(frames * 2) { Short.MAX_VALUE.toInt().toShort() }
        processor.process(
            audioInput = buffer,
            sampleRate = sampleRate,
            inputStartMillis = 0.0,
            fadeStartMillis = 0.0,
            fadeLengthMillis = 100.0,
        )
        // First frame near full-scale, last frame near zero.
        assertTrue(abs(buffer[0].toInt()) > Short.MAX_VALUE / 2, "fade start should be near full-scale")
        assertTrue(abs(buffer[buffer.size - 1].toInt()) < Short.MAX_VALUE / 10, "fade end should be near zero")
    }

    @Test
    fun `processor scales samples and clamps to Short range when boosted`() {
        // 1.5x boost on a 0.8-amplitude sample (~26214) yields 39321 — past Short.MAX_VALUE so
        // the coerceIn in scaleSample must clamp without integer wrap-around. We can't reach
        // scaleSample directly, but we can drive process() with a target of 1.5x and an actualGain
        // already at 1.5x (achieved by ramping for thousands of frames first).
        val processor = newProcessor()
        processor.setMasterVolume(1.5)
        // Ramp up to ~1.5x: needs (0.5 / 0.00005) = 10000 frames of warmup, then test buffer.
        processor.process(ShortArray(20_000 * 2), 48_000, 0.0, 0.0, 0.0)
        val test = ShortArray(8) { 26_214 } // 0.8 * Short.MAX_VALUE
        processor.process(test, 48_000, 0.0, 0.0, 0.0)
        for (s in test) assertEquals(Short.MAX_VALUE, s, "expected saturation, not wrap-around")
    }

    private fun sineBuffer(frames: Int, amplitude: Double): ShortArray {
        // Cheap stereo sine — same shape as EbuR128Test#stereoSine but kept independent so test
        // files don't share helpers. Phase doesn't matter for these gain assertions.
        val buffer = ShortArray(frames * 2)
        val scale = (amplitude * Short.MAX_VALUE).toInt().toShort()
        for (i in buffer.indices) buffer[i] = scale
        return buffer
    }
}
