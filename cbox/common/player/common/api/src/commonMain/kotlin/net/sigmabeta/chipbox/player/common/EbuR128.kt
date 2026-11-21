package net.sigmabeta.chipbox.player.common

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * BS.1770-4 / EBU R 128 loudness measurer. Kotlin port of the parts of libebur128 chipbox needs:
 * integrated loudness (LUFS, gated 400 ms windows) and true peak (dBTP, 4× polyphase oversample).
 *
 * Designed for stereo 16-bit PCM as the rest of the player produces, but generalises to any
 * channel count with all-1.0 channel weights (BS.1770 assigns higher weights to surround channels
 * — irrelevant here). Sample rate is arbitrary; the K-weighting biquads are re-derived from the
 * analog prototype via bilinear transform at construction.
 *
 * One instance per measurement; not thread-safe. The caller feeds frames via [process] in order;
 * [integratedLoudness] and [truePeakDbtp] are valid at any time but stabilise once enough audio
 * has passed through (the first integrated value appears after the first 400 ms of audio; before
 * that [integratedLoudness] returns [Double.NaN]).
 *
 * ### Output sentinels
 * - [Double.NaN] from [integratedLoudness] means "no usable measurement yet" (fewer than one
 *   400 ms block of audio has passed through, or every block was gated out as silence).
 * - [Double.NEGATIVE_INFINITY] from [truePeakDbtp] means "no audible peak at all".
 */
class EbuR128(
    private val sampleRate: Int,
    private val channels: Int = 2,
) {
    private val preFilters = Array(channels) { Biquad.kWeightingShelf(sampleRate) }
    private val rlbFilters = Array(channels) { Biquad.kWeightingHighPass(sampleRate) }

    /** Frames per 100 ms sub-block. A 400 ms gating block is 4 of these. */
    private val subBlockFrames = (sampleRate / SUB_BLOCKS_PER_SECOND).coerceAtLeast(1)

    /** Ring of sub-block partial sums (sum of K-weighted squares over channels and frames). */
    private val subBlockSums = DoubleArray(SUB_BLOCK_COUNT)
    private var subBlockPosition = 0
    private var subBlockFrameCounter = 0
    private var filledSubBlocks = 0

    /** Per-block mean-square values, one entry per completed 400 ms gating block. Stage-1 and
     *  stage-2 gating happen at finalize. */
    private val blockMeanSquares = ArrayList<Double>()

    private val truePeak = TruePeak(channels)

    /** One channel of the current buffer: raw samples, then K-weighted in place. */
    private var channelSamples = DoubleArray(0)

    /** Per frame of the current buffer: the sum over channels of the K-weighted squares. */
    private var frameSquares = DoubleArray(0)

    /**
     * Feed [frameCount] interleaved frames from [buffer] into the measurer. `frameCount * channels`
     * must not exceed `buffer.size`. Safe to call with `frameCount <= 0`.
     *
     * Works one channel at a time over the whole buffer, so each filter keeps its state in locals
     * instead of object fields. Every value is still computed from the same operands in the same
     * order as a frame-by-frame walk (the per-frame channel sum adds channels in index order, and
     * the sub-block sums add frames in time order), so the results are bit-identical to it.
     */
    fun process(buffer: ShortArray, frameCount: Int) {
        if (frameCount <= 0) return
        if (channelSamples.size < frameCount) {
            channelSamples = DoubleArray(frameCount)
            frameSquares = DoubleArray(frameCount)
        }
        val samples = channelSamples
        val squares = frameSquares
        squares.fill(0.0, 0, frameCount)
        for (c in 0 until channels) {
            var i = c
            for (f in 0 until frameCount) {
                samples[f] = buffer[i].toDouble() * INV_FULL_SCALE
                i += channels
            }
            truePeak.process(c, samples, frameCount)
            preFilters[c].processInPlace(samples, frameCount)
            rlbFilters[c].processInPlace(samples, frameCount)
            for (f in 0 until frameCount) {
                val kw = samples[f]
                squares[f] += kw * kw
            }
        }
        for (f in 0 until frameCount) {
            subBlockSums[subBlockPosition] += squares[f]
            subBlockFrameCounter++
            if (subBlockFrameCounter >= subBlockFrames) rotateSubBlock()
        }
    }

    /**
     * Close out the current 100 ms sub-block: once the ring holds a full 400 ms (4 sub-blocks),
     * record that block's mean square, then advance the ring head and clear the slot it now owns.
     */
    private fun rotateSubBlock() {
        if (filledSubBlocks < SUB_BLOCK_COUNT) filledSubBlocks++
        if (filledSubBlocks == SUB_BLOCK_COUNT) {
            var blockSum = 0.0
            for (k in 0 until SUB_BLOCK_COUNT) blockSum += subBlockSums[k]
            blockMeanSquares.add(blockSum / (subBlockFrames * SUB_BLOCK_COUNT))
        }
        subBlockPosition = (subBlockPosition + 1) % SUB_BLOCK_COUNT
        subBlockSums[subBlockPosition] = 0.0
        subBlockFrameCounter = 0
    }

    /**
     * Integrated loudness across all audio fed so far, in LUFS. Applies BS.1770's two-stage
     * gating: an absolute -70 LUFS threshold first, then a relative -10 LU threshold below the
     * mean of the absolute-gated blocks. Returns [Double.NaN] if no blocks have been gathered
     * yet or every block was gated out as silence.
     */
    fun integratedLoudness(): Double {
        val absoluteThreshold = lufsToMeanSquare(ABSOLUTE_GATE_LUFS)
        val absoluteMean = gatedMeanSquare(absoluteThreshold)
        if (absoluteMean.isNaN()) return Double.NaN
        val relativeThreshold = lufsToMeanSquare(meanSquareToLufs(absoluteMean) - RELATIVE_GATE_LU)
        val relativeMean = gatedMeanSquare(maxOf(absoluteThreshold, relativeThreshold))
        return if (relativeMean.isNaN()) Double.NaN else meanSquareToLufs(relativeMean)
    }

    /**
     * Mean of the per-block mean-squares at or above [threshold], or [Double.NaN] if no block
     * clears it. Both BS.1770 gating passes go through here: the absolute gate, then the relative
     * gate — for which we pass `max(absolute, relative)` so a kept block must clear both.
     */
    private fun gatedMeanSquare(threshold: Double): Double {
        var sum = 0.0
        var count = 0
        for (ms in blockMeanSquares) {
            if (ms >= threshold) {
                sum += ms
                count++
            }
        }
        return if (count == 0) Double.NaN else sum / count
    }

    /**
     * Peak signal level in dBTP, computed against a 4× oversampled reconstruction of the input.
     * [Double.NEGATIVE_INFINITY] when no non-zero samples have been seen. Values above 0 dBTP
     * are possible (and meaningful) — inter-sample peaks can exceed full-scale digital peak.
     */
    fun truePeakDbtp(): Double {
        val peakLinear = truePeak.maxAbs
        if (peakLinear <= 0.0) return Double.NEGATIVE_INFINITY
        return DBFS_VOLTAGE_FACTOR * log10(peakLinear)
    }

    companion object {
        /** 1.0 LUFS = -0.691 + 10·log10(mean K-weighted square). The offset comes from K's gain. */
        private const val LUFS_OFFSET = -0.691

        /** BS.1770 stage-one gate: drop any block measuring below this LUFS before averaging. */
        private const val ABSOLUTE_GATE_LUFS = -70.0

        /** BS.1770 stage-two gate: drop any block more than this many LU below the ungated mean. */
        private const val RELATIVE_GATE_LU = 10.0

        /** 4 × 100 ms = 400 ms = one BS.1770 measurement block (75 % overlap). */
        private const val SUB_BLOCK_COUNT = 4
        private const val SUB_BLOCKS_PER_SECOND = 10

        /** Power-to-LUFS conversion uses factor 10 (mean-square is a power quantity). */
        private const val DBFS_POWER_FACTOR = 10.0

        /** Amplitude-to-dB conversion uses factor 20 (true peak is an amplitude quantity). */
        private const val DBFS_VOLTAGE_FACTOR = 20.0

        /** 1.0 / Short.MAX_VALUE — normalises signed 16-bit PCM to roughly ±1.0. */
        private const val INV_FULL_SCALE = 1.0 / 32767.0

        private fun lufsToMeanSquare(lufs: Double): Double =
            10.0.pow((lufs - LUFS_OFFSET) / DBFS_POWER_FACTOR)

        private fun meanSquareToLufs(meanSquare: Double): Double =
            LUFS_OFFSET + DBFS_POWER_FACTOR * log10(meanSquare)
    }
}

/**
 * Direct-form-I biquad. Used for the two K-weighting stages (high-shelf pre-filter and
 * "RLB" high-pass). Stateful: instances are not reusable across measurements without [reset].
 */
internal class Biquad(
    internal val b0: Double,
    internal val b1: Double,
    internal val b2: Double,
    internal val a1: Double,
    internal val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    /**
     * Filter the first [count] values of [samples] in place, continuing from the state the last
     * call left. The state lives in locals for the loop and is written back once at the end.
     */
    fun processInPlace(samples: DoubleArray, count: Int) {
        var in1 = x1
        var in2 = x2
        var out1 = y1
        var out2 = y2
        for (i in 0 until count) {
            val in0 = samples[i]
            val out0 = b0 * in0 + b1 * in1 + b2 * in2 - a1 * out1 - a2 * out2
            in2 = in1
            in1 = in0
            out2 = out1
            out1 = out0
            samples[i] = out0
        }
        x1 = in1
        x2 = in2
        y1 = out1
        y2 = out2
    }

    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    companion object {
        // BS.1770-4 K-weighting stage 1 — high-shelf design parameters (analog prototype, before
        // the per-rate bilinear transform): +4 dB shelf centred at 1681.974 Hz, Q ≈ 0.707.
        private const val SHELF_FREQ_HZ = 1681.974450955533
        private const val SHELF_GAIN_DB = 3.999843853973347
        private const val SHELF_Q = 0.7071752369554196

        // vb = vh raised to this exponent — the shelf's mid-band gain factor (libebur128's value).
        private const val SHELF_VB_EXPONENT = 0.4996667741545416

        // BS.1770-4 K-weighting stage 2 — high-pass design parameters: 38.135 Hz, Q ≈ 0.5.
        private const val HIGH_PASS_FREQ_HZ = 38.13547087602444
        private const val HIGH_PASS_Q = 0.5003270373238773

        // Amplitude dB conversion divisor (amplitude dB = 20·log10).
        private const val DB_AMPLITUDE_DIVISOR = 20.0

        /**
         * BS.1770-4 stage 1: +4 dB high-shelf at 1681.974 Hz, Q ≈ 0.707. Models the head/torso
         * acoustic shadow's bias toward higher frequencies. Coefficients are derived from the
         * analog prototype via bilinear transform at [sampleRate], so this matches the spec at
         * any rate — not just 48 kHz.
         */
        fun kWeightingShelf(sampleRate: Int): Biquad {
            val k = tan(PI * SHELF_FREQ_HZ / sampleRate)
            val vh = 10.0.pow(SHELF_GAIN_DB / DB_AMPLITUDE_DIVISOR)
            val vb = vh.pow(SHELF_VB_EXPONENT)
            val a0 = 1.0 + k / SHELF_Q + k * k
            return Biquad(
                b0 = (vh + vb * k / SHELF_Q + k * k) / a0,
                b1 = 2.0 * (k * k - vh) / a0,
                b2 = (vh - vb * k / SHELF_Q + k * k) / a0,
                a1 = 2.0 * (k * k - 1.0) / a0,
                a2 = (1.0 - k / SHELF_Q + k * k) / a0,
            )
        }

        /**
         * BS.1770-4 stage 2: 2nd-order high-pass at 38.135 Hz, Q ≈ 0.5. Attenuates inaudible
         * sub-bass that would otherwise dominate the mean-square measurement.
         */
        fun kWeightingHighPass(sampleRate: Int): Biquad {
            val k = tan(PI * HIGH_PASS_FREQ_HZ / sampleRate)
            val a0 = 1.0 + k / HIGH_PASS_Q + k * k
            return Biquad(
                b0 = 1.0,
                b1 = -2.0,
                b2 = 1.0,
                a1 = 2.0 * (k * k - 1.0) / a0,
                a2 = (1.0 - k / HIGH_PASS_Q + k * k) / a0,
            )
        }
    }
}

/**
 * 4× polyphase FIR oversampler used for true-peak detection. Tracks the running maximum
 * absolute value of both the raw input samples and the three interpolated samples between
 * each pair of inputs. Coefficients are a Blackman-windowed sinc designed at construction so
 * the filter passes the original band ([0, fs/2]) and attenuates the alias band ([fs/2, 2·fs]).
 *
 * Input arrives a channel-buffer at a time. Each channel carries the last `TAPS_PER_PHASE - 1`
 * samples of its previous buffer, which are copied ahead of the new samples into one contiguous
 * window, so tap t of the sample at window index k is simply `window[k - t]`. State is
 * independent across channels.
 *
 * ### Skipping work that cannot change the peak
 * An interpolated sample is a 12-tap dot product, so its magnitude is at most the phase's L1
 * coefficient norm times the loudest of those 12 samples. Each chunk of [SKIP_CHUNK_FRAMES] is
 * checked against that bound first: if even the bound for every sample in the chunk (and its 11
 * leading history samples) is at or below the running [maxAbs], no candidate in the chunk can raise
 * it, and the 48 multiply-adds per sample are skipped. The bound carries [BOUND_SLACK] of headroom,
 * which is far more than the rounding a 12-term sum can accumulate, so a skipped chunk is exactly
 * one whose filtered output would have left [maxAbs] unchanged. Filtered chunks use the same
 * products in the same order as a per-sample walk. Either way the result is bit-identical.
 */
internal class TruePeak(private val channels: Int) {
    internal val coefficients: Array<DoubleArray> = buildCoefficients()

    /** The largest phase L1 norm, padded by [BOUND_SLACK]. Phases sum to 1, so this is >= 1. */
    private val gainBound = coefficients.maxOf { phase -> phase.sumOf { abs(it) } } * (1.0 + BOUND_SLACK)

    /** Per channel: the last [HISTORY] samples fed, oldest first. Zero before any input. */
    private val history = Array(channels) { DoubleArray(HISTORY) }

    /** [HISTORY] samples of carried history followed by the current channel-buffer. */
    private var window = DoubleArray(HISTORY)

    var maxAbs: Double = 0.0
        private set

    /**
     * Feed the first [count] samples of [samples], all for [channel], in time order. Updates
     * [maxAbs] with the largest of the raw samples and their 4 polyphase-interpolated outputs.
     */
    fun process(channel: Int, samples: DoubleArray, count: Int) {
        if (count <= 0) return
        val end = HISTORY + count
        if (window.size < end) window = DoubleArray(end)
        val w = window
        val hist = history[channel]
        hist.copyInto(w, destinationOffset = 0)
        samples.copyInto(w, destinationOffset = HISTORY, startIndex = 0, endIndex = count)
        var start = HISTORY
        while (start < end) {
            val chunkEnd = minOf(start + SKIP_CHUNK_FRAMES, end)
            if (!cannotRaisePeak(w, start, chunkEnd)) filter(w, start, chunkEnd)
            start = chunkEnd
        }
        w.copyInto(hist, destinationOffset = 0, startIndex = end - HISTORY, endIndex = end)
    }

    /** True when no sample in `w[from until to]`, raw or interpolated, can exceed [maxAbs]. */
    private fun cannotRaisePeak(w: DoubleArray, from: Int, to: Int): Boolean {
        var windowMax = 0.0
        for (k in from - HISTORY until to) {
            val a = abs(w[k])
            if (a > windowMax) windowMax = a
        }
        return windowMax * gainBound <= maxAbs
    }

    /** The full oversampler over `w[from until to]`; tap t of index k is `w[k - t]`. */
    private fun filter(w: DoubleArray, from: Int, to: Int) {
        var peak = maxAbs
        for (k in from until to) {
            val a = abs(w[k])
            if (a > peak) peak = a
            for (p in 0 until PHASES) {
                val co = coefficients[p]
                var sum = 0.0
                for (t in 0 until TAPS_PER_PHASE) {
                    sum += w[k - t] * co[t]
                }
                val mag = abs(sum)
                if (mag > peak) peak = mag
            }
        }
        maxAbs = peak
    }

    companion object {
        private const val PHASES = 4
        private const val TAPS_PER_PHASE = 12
        private const val HISTORY = TAPS_PER_PHASE - 1

        /**
         * Frames per skip decision. Smaller chunks skip more of a buffer once the peak is
         * established, at the cost of re-reading [HISTORY] samples per chunk for the bound.
         */
        private const val SKIP_CHUNK_FRAMES = 256

        /**
         * Relative headroom on the skip bound. A 12-term double dot product rounds by well under
         * 1e-14 of the sum of its absolute terms, so 1e-9 can never let a real peak through.
         */
        private const val BOUND_SLACK = 1e-9

        // Blackman window: w[n] = a0 − a1·cos(2πn/(N−1)) + a2·cos(4πn/(N−1)). The harmonic 4 names
        // the second cosine's multiplier; the `2` of the first term is detekt-ignored.
        private const val BLACKMAN_A0 = 0.42
        private const val BLACKMAN_A1 = 0.5
        private const val BLACKMAN_A2 = 0.08
        private const val BLACKMAN_A2_HARMONIC = 4

        /**
         * Design a 4-phase × 12-tap polyphase low-pass FIR by Blackman-windowing the
         * theoretical sinc-with-cutoff-π/4 (i.e. the original Nyquist after 4× upsample) and
         * decomposing into phases. Each phase's coefficients are independently normalised so
         * its DC gain is 1.0 — the natural decomposition would have each phase carrying 1/L of
         * the DC gain, which we'd then have to multiply back; normalising in-place is the same
         * thing but clearer.
         */
        private fun buildCoefficients(): Array<DoubleArray> {
            val totalTaps = PHASES * TAPS_PER_PHASE
            val center = (totalTaps - 1) / 2.0
            val prototype = DoubleArray(totalTaps)
            for (i in 0 until totalTaps) {
                val n = i - center
                val sincArg = PI * n / PHASES
                val sincVal = if (sincArg == 0.0) 1.0 else sin(sincArg) / sincArg
                val w = BLACKMAN_A0 -
                    BLACKMAN_A1 * cos(2 * PI * i / (totalTaps - 1)) +
                    BLACKMAN_A2 * cos(BLACKMAN_A2_HARMONIC * PI * i / (totalTaps - 1))
                prototype[i] = sincVal * w
            }
            val phases = Array(PHASES) { DoubleArray(TAPS_PER_PHASE) }
            for (p in 0 until PHASES) {
                var phaseSum = 0.0
                for (t in 0 until TAPS_PER_PHASE) {
                    val proto = prototype[p + PHASES * t]
                    phases[p][t] = proto
                    phaseSum += proto
                }
                if (phaseSum != 0.0) {
                    for (t in 0 until TAPS_PER_PHASE) phases[p][t] /= phaseSum
                }
            }
            return phases
        }
    }
}
