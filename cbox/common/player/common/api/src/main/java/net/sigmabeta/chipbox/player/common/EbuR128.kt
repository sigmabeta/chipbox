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

    /**
     * Feed [frameCount] interleaved frames from [buffer] into the measurer. `frameCount * channels`
     * must not exceed `buffer.size`. Safe to call with `frameCount <= 0`.
     */
    fun process(buffer: ShortArray, frameCount: Int) {
        if (frameCount <= 0) return
        var i = 0
        for (f in 0 until frameCount) {
            var weightedSquareSum = 0.0
            for (c in 0 until channels) {
                val raw = buffer[i + c].toDouble() * INV_FULL_SCALE
                truePeak.process(c, raw)
                val kw = rlbFilters[c].process(preFilters[c].process(raw))
                weightedSquareSum += kw * kw
            }
            i += channels
            subBlockSums[subBlockPosition] += weightedSquareSum
            subBlockFrameCounter++
            if (subBlockFrameCounter >= subBlockFrames) {
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
        }
    }

    /**
     * Integrated loudness across all audio fed so far, in LUFS. Applies BS.1770's two-stage
     * gating: an absolute -70 LUFS threshold first, then a relative -10 LU threshold below the
     * mean of the absolute-gated blocks. Returns [Double.NaN] if no blocks have been gathered
     * yet or every block was gated out as silence.
     */
    fun integratedLoudness(): Double {
        if (blockMeanSquares.isEmpty()) return Double.NaN
        val absoluteThreshold = lufsToMeanSquare(ABSOLUTE_GATE_LUFS)
        var sumAbs = 0.0
        var countAbs = 0
        for (ms in blockMeanSquares) {
            if (ms >= absoluteThreshold) {
                sumAbs += ms
                countAbs++
            }
        }
        if (countAbs == 0) return Double.NaN
        val relativeThreshold = lufsToMeanSquare(
            meanSquareToLufs(sumAbs / countAbs) - RELATIVE_GATE_LU
        )
        var sumRel = 0.0
        var countRel = 0
        for (ms in blockMeanSquares) {
            if (ms >= absoluteThreshold && ms >= relativeThreshold) {
                sumRel += ms
                countRel++
            }
        }
        if (countRel == 0) return Double.NaN
        return meanSquareToLufs(sumRel / countRel)
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
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(x0: Double): Double {
        val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x0
        y2 = y1
        y1 = y0
        return y0
    }

    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    companion object {
        /**
         * BS.1770-4 stage 1: +4 dB high-shelf at 1681.974 Hz, Q ≈ 0.707. Models the head/torso
         * acoustic shadow's bias toward higher frequencies. Coefficients are derived from the
         * analog prototype via bilinear transform at [sampleRate], so this matches the spec at
         * any rate — not just 48 kHz.
         */
        fun kWeightingShelf(sampleRate: Int): Biquad {
            val f0 = 1681.974450955533
            val gainDb = 3.999843853973347
            val q = 0.7071752369554196
            val k = tan(PI * f0 / sampleRate)
            val vh = 10.0.pow(gainDb / 20.0)
            val vb = vh.pow(0.4996667741545416)
            val a0 = 1.0 + k / q + k * k
            return Biquad(
                b0 = (vh + vb * k / q + k * k) / a0,
                b1 = 2.0 * (k * k - vh) / a0,
                b2 = (vh - vb * k / q + k * k) / a0,
                a1 = 2.0 * (k * k - 1.0) / a0,
                a2 = (1.0 - k / q + k * k) / a0,
            )
        }

        /**
         * BS.1770-4 stage 2: 2nd-order high-pass at 38.135 Hz, Q ≈ 0.5. Attenuates inaudible
         * sub-bass that would otherwise dominate the mean-square measurement.
         */
        fun kWeightingHighPass(sampleRate: Int): Biquad {
            val f0 = 38.13547087602444
            val q = 0.5003270373238773
            val k = tan(PI * f0 / sampleRate)
            val a0 = 1.0 + k / q + k * k
            return Biquad(
                b0 = 1.0,
                b1 = -2.0,
                b2 = 1.0,
                a1 = 2.0 * (k * k - 1.0) / a0,
                a2 = (1.0 - k / q + k * k) / a0,
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
 * Per-channel circular history of [TAPS_PER_PHASE] previous samples so the polyphase filter
 * can stride backwards through them. State is independent across channels.
 */
internal class TruePeak(private val channels: Int) {
    private val coefficients: Array<DoubleArray> = buildCoefficients()
    private val history = Array(channels) { DoubleArray(TAPS_PER_PHASE) }
    private val historyPos = IntArray(channels)

    var maxAbs: Double = 0.0
        private set

    /**
     * Feed one raw sample [sample] for [channel]. Updates [maxAbs] with the larger of the raw
     * sample and the 4 polyphase-interpolated outputs.
     */
    fun process(channel: Int, sample: Double) {
        val a = abs(sample)
        if (a > maxAbs) maxAbs = a

        val hist = history[channel]
        val pos = historyPos[channel]
        hist[pos] = sample

        for (p in 0 until PHASES) {
            val co = coefficients[p]
            var sum = 0.0
            for (t in 0 until TAPS_PER_PHASE) {
                val idx = ((pos - t) + TAPS_PER_PHASE) % TAPS_PER_PHASE
                sum += hist[idx] * co[t]
            }
            val mag = abs(sum)
            if (mag > maxAbs) maxAbs = mag
        }
        historyPos[channel] = (pos + 1) % TAPS_PER_PHASE
    }

    companion object {
        private const val PHASES = 4
        private const val TAPS_PER_PHASE = 12

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
                val w = 0.42 -
                    0.5 * cos(2 * PI * i / (totalTaps - 1)) +
                    0.08 * cos(4 * PI * i / (totalTaps - 1))
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
