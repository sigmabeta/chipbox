package net.sigmabeta.chipbox.player.resampler

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResamplerTest {

    private fun impls(): List<Resampler> = listOf(LinearResampler(), CubicResampler())

    /** Interleaved-stereo sine, identical on both channels. */
    private fun sine(frames: Int, freqHz: Double, rate: Int, amp: Int = 10_000): ShortArray {
        val out = ShortArray(frames * 2)
        for (n in 0 until frames) {
            val v = (amp * sin(2.0 * PI * freqHz * n / rate)).roundToInt().toShort()
            out[n * 2] = v
            out[n * 2 + 1] = v
        }
        return out
    }

    private fun runWhole(r: Resampler, input: ShortArray, inFrames: Int, inRate: Int, outRate: Int): ShortArray {
        val out = ShortArray(r.maxOutputFrames(inFrames, inRate, outRate) * 2)
        val n = r.process(input, inFrames, inRate, outRate, out)
        return out.copyOf(n * 2)
    }

    @Test
    fun `cumulative output tracks the rate ratio with no drift across many buffers`() {
        // Stream many buffers and check the running total stays locked to the ideal ratio — the
        // deficit is only the kernel's fixed tail-hold latency, constant in the buffer count (a
        // floating-point phase would let the error grow with N and slowly detune the track).
        val inFrames = 4096
        val buffers = 50
        for ((inRate, outRate) in listOf(32006 to 48000, 48000 to 32006, 44100 to 48000)) {
            for (r in impls()) {
                val out = ShortArray(r.maxOutputFrames(inFrames, inRate, outRate) * 2)
                var total = 0L
                repeat(buffers) { total += r.process(sine(inFrames, 440.0, inRate), inFrames, inRate, outRate, out) }
                val ideal = (inFrames.toLong() * buffers) * outRate.toDouble() / inRate
                assertTrue(
                    abs(total - ideal) <= 8.0,
                    "${r::class.simpleName} $inRate->$outRate total $total, ideal ~$ideal",
                )
            }
        }
    }

    @Test
    fun `process never exceeds maxOutputFrames`() {
        for (inFrames in listOf(1, 64, 2048, 4096)) {
            for (r in impls()) {
                val cap = r.maxOutputFrames(inFrames, 32006, 48000)
                val out = ShortArray(cap * 2)
                val n = r.process(sine(inFrames, 300.0, 32006), inFrames, 32006, 48000, out)
                assertTrue(n <= cap, "${r::class.simpleName} produced $n > cap $cap for $inFrames")
            }
        }
    }

    @Test
    fun `splitting input across calls yields identical output (cross-buffer continuity)`() {
        // The streaming history must make a split feed bit-identical to a single feed — this is the
        // guard against a click at every buffer boundary.
        val inFrames = 2000
        val input = sine(inFrames, 660.0, 32006)
        for (r in impls()) {
            val whole = runWhole(r, input, inFrames, 32006, 48000)

            val split = impls().first { it::class == r::class }
            val firstHalf = input.copyOfRange(0, 1000 * 2)
            val secondHalf = input.copyOfRange(1000 * 2, inFrames * 2)
            val a = runWhole(split, firstHalf, 1000, 32006, 48000)
            val b = runWhole(split, secondHalf, 1000, 32006, 48000)
            val joined = a + b

            assertEquals(whole.size, joined.size, "${r::class.simpleName}: split changed frame count")
            assertTrue(whole.contentEquals(joined), "${r::class.simpleName}: split is not bit-identical")
        }
    }

    @Test
    fun `steady-state DC is preserved`() {
        val inFrames = 1000
        val dc = ShortArray(inFrames * 2) { 8000 }
        for (r in impls()) {
            val out = runWhole(r, dc, inFrames, 32000, 48000)
            val frames = out.size / 2
            // Skip the kernel's startup priming (zero history); check well into steady state.
            for (f in (frames * 3 / 4) until frames) {
                assertEquals(8000.toShort(), out[f * 2], "${r::class.simpleName} L drifted at frame $f")
                assertEquals(8000.toShort(), out[f * 2 + 1], "${r::class.simpleName} R drifted at frame $f")
            }
        }
    }

    @Test
    fun `reset restores a fresh-instance result`() {
        val inFrames = 512
        val input = sine(inFrames, 1000.0, 44100)
        for (r in impls()) {
            val fresh = runWhole(impls().first { it::class == r::class }, input, inFrames, 44100, 48000)
            runWhole(r, sine(inFrames, 1234.0, 44100), inFrames, 44100, 48000) // dirty its state
            r.reset()
            val afterReset = runWhole(r, input, inFrames, 44100, 48000)
            assertTrue(fresh.contentEquals(afterReset), "${r::class.simpleName}: reset did not clear state")
        }
    }

    @Test
    fun `a rate change between calls resets carried state`() {
        // Feeding a buffer at a new rate must be bit-identical to a fresh instance at that rate —
        // the resampler drops phase + history on the change rather than interpolating across it.
        val inFrames = 800
        for (r in impls()) {
            runWhole(r, sine(inFrames, 500.0, 32006), inFrames, 32006, 48000) // run at one rate
            val afterChange = runWhole(r, sine(inFrames, 500.0, 44100), inFrames, 44100, 48000)
            val fresh = runWhole(impls().first { it::class == r::class }, sine(inFrames, 500.0, 44100), inFrames, 44100, 48000)
            assertTrue(
                fresh.contentEquals(afterChange),
                "${r::class.simpleName}: rate change did not reset carried state",
            )
        }
    }
}
