package net.sigmabeta.chipbox.player.resampler

import kotlin.math.roundToInt

/**
 * Shared streaming machinery for the interpolating [Resampler]s. Subclasses supply only the
 * interpolation [kernel] (and, via the constructor, its tap geometry); this base owns the phase
 * advance, the bounds that decide which output frames can be produced from the current input
 * buffer, and the per-channel history carried across [process] calls so a kernel that reaches past
 * a buffer edge stays continuous.
 *
 * The `(inputRate, outputRate)` pair is **not** fixed at construction — it arrives with each
 * [process] call. A change since the previous call is a stream discontinuity (e.g. the next track
 * in a setlist has a different native rate), so the carried phase + history are dropped via [reset]
 * before the new ratio is used. Within a single rate the phase is an **exact rational**: an integer
 * input index [inPos] plus a fraction [phaseNum]`/`[outputRate]. Each output advances the input
 * position by `inputRate/outputRate` via integer arithmetic, so there is no floating-point drift —
 * the pitch can't wander over a long track, and feeding a stream split across buffers is
 * bit-identical to feeding it whole. A `Double` is formed only to evaluate the kernel between two
 * samples.
 *
 * Coordinates are input frames relative to the current buffer's first frame; a frame at relative
 * index `rel` resolves to history when `rel < 0` and to [input] otherwise.
 *
 * @param taps total samples the kernel reads (linear 2, cubic 4).
 * @param leftTaps how many of those sit to the left of [inPos] (linear 0, cubic 1).
 */
abstract class StreamingResampler(
    private val taps: Int,
    private val leftTaps: Int,
) : Resampler {

    private val rightTaps = taps - 1 - leftTaps

    /** Frames retained from the tail of each buffer so the next call's left/right reach is backed. */
    private val historyFrames = taps - 1

    private val history = ShortArray(historyFrames * SHORTS_PER_FRAME)
    private val nextHistory = ShortArray(historyFrames * SHORTS_PER_FRAME)

    // The rate pair the carried state belongs to; a change in [process] is a discontinuity.
    private var inputRate = 0
    private var outputRate = 0

    /** Integer input index (relative to the current buffer) of the next output frame; goes negative
     *  after a carry when the next output's left context lives in [history]. */
    private var inPos = 0

    /** Fractional phase numerator in `[0, outputRate)`; the read position is `inPos + phaseNum/out`. */
    private var phaseNum = 0

    /** Sample at offset [rel] frames from the current buffer's start (negative → history),
     *  [channel] 0=L/1=R. Callers must keep `rel` within `[-historyFrames, inputFrames - 1]`. */
    protected fun sample(input: ShortArray, rel: Int, channel: Int): Int =
        if (rel < 0) {
            history[(historyFrames + rel) * SHORTS_PER_FRAME + channel].toInt()
        } else {
            input[rel * SHORTS_PER_FRAME + channel].toInt()
        }

    /** Interpolated value for [channel] at integer input index [i] plus [frac] in [0, 1). Reads via
     *  [sample]; returns an unclamped, un-rounded amplitude (the base rounds + clamps to 16-bit). */
    protected abstract fun kernel(input: ShortArray, i: Int, frac: Double, channel: Int): Double

    final override fun maxOutputFrames(inputFrames: Int, inputRate: Int, outputRate: Int): Int =
        ((inputFrames.toLong() * outputRate) / inputRate).toInt() + OUTPUT_HEADROOM_FRAMES

    final override fun reset() {
        inPos = 0
        phaseNum = 0
        history.fill(0)
    }

    final override fun process(
        input: ShortArray,
        inputFrames: Int,
        inputRate: Int,
        outputRate: Int,
        output: ShortArray,
    ): Int {
        require(inputRate > 0 && outputRate > 0) { "rates must be positive: $inputRate -> $outputRate" }
        // A rate change since the last call is a discontinuity — drop the carried phase + history
        // before interpolating with the new ratio.
        if (inputRate != this.inputRate || outputRate != this.outputRate) {
            this.inputRate = inputRate
            this.outputRate = outputRate
            reset()
        }

        var outFrames = 0
        while (true) {
            // Need the kernel's rightmost sample inside this buffer; otherwise it lives in the next
            // buffer and this output frame waits (its phase carries to the next call unchanged).
            if (inPos + rightTaps > inputFrames - 1) break
            val frac = phaseNum.toDouble() / outputRate
            val outIndex = outFrames * SHORTS_PER_FRAME
            output[outIndex] = kernel(input, inPos, frac, 0).toClampedShort()
            output[outIndex + 1] = kernel(input, inPos, frac, 1).toClampedShort()
            outFrames++

            phaseNum += inputRate
            inPos += phaseNum / outputRate
            phaseNum %= outputRate
        }

        // Rebase to the next buffer (phase fraction is unchanged) and stash the trailing frames the
        // next call's kernel will reach back into. Built from the tail of (history ++ input) so it's
        // correct even if a buffer were shorter than the history window (real buffers never are).
        inPos -= inputFrames
        val combined = historyFrames + inputFrames
        for (h in 0 until historyFrames) {
            val idx = combined - historyFrames + h
            val dst = h * SHORTS_PER_FRAME
            if (idx < historyFrames) {
                nextHistory[dst] = history[idx * SHORTS_PER_FRAME]
                nextHistory[dst + 1] = history[idx * SHORTS_PER_FRAME + 1]
            } else {
                val j = (idx - historyFrames) * SHORTS_PER_FRAME
                nextHistory[dst] = input[j]
                nextHistory[dst + 1] = input[j + 1]
            }
        }
        nextHistory.copyInto(history)

        return outFrames
    }

    private fun Double.toClampedShort(): Short =
        roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private companion object {
        const val SHORTS_PER_FRAME = 2

        /** Slack over the ideal output count to absorb the carried fractional phase + rounding. */
        const val OUTPUT_HEADROOM_FRAMES = 2
    }
}
