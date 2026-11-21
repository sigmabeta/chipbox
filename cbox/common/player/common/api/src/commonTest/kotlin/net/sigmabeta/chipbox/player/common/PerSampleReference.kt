package net.sigmabeta.chipbox.player.common

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * The frame-by-frame loudness measurer the buffer-wise [EbuR128] replaced, kept verbatim apart from
 * taking its filter coefficients from the production designs. The PCM cache header stores the
 * measured LUFS and true peak, so the production code must match this to the last bit.
 */
internal class PerSampleEbuR128(sampleRate: Int, private val channels: Int) {
    private val preFilters = Array(channels) { PerSampleBiquad(Biquad.kWeightingShelf(sampleRate)) }
    private val rlbFilters = Array(channels) { PerSampleBiquad(Biquad.kWeightingHighPass(sampleRate)) }
    private val subBlockFrames = (sampleRate / SUB_BLOCKS_PER_SECOND).coerceAtLeast(1)
    private val subBlockSums = DoubleArray(SUB_BLOCK_COUNT)
    private var subBlockPosition = 0
    private var subBlockFrameCounter = 0
    private var filledSubBlocks = 0
    private val blockMeanSquares = ArrayList<Double>()
    private val truePeak = ModuloTruePeak(channels, TruePeak(1).coefficients)

    fun process(buffer: ShortArray, frameCount: Int) {
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
            if (subBlockFrameCounter >= subBlockFrames) rotateSubBlock()
        }
    }

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

    fun integratedLoudness(): Double {
        val absoluteThreshold = lufsToMeanSquare(ABSOLUTE_GATE_LUFS)
        val absoluteMean = gatedMeanSquare(absoluteThreshold)
        if (absoluteMean.isNaN()) return Double.NaN
        val relativeThreshold = lufsToMeanSquare(meanSquareToLufs(absoluteMean) - RELATIVE_GATE_LU)
        val relativeMean = gatedMeanSquare(maxOf(absoluteThreshold, relativeThreshold))
        return if (relativeMean.isNaN()) Double.NaN else meanSquareToLufs(relativeMean)
    }

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

    fun truePeakDbtp(): Double {
        val peakLinear = truePeak.maxAbs
        if (peakLinear <= 0.0) return Double.NEGATIVE_INFINITY
        return 20.0 * log10(peakLinear)
    }

    private companion object {
        const val LUFS_OFFSET = -0.691
        const val ABSOLUTE_GATE_LUFS = -70.0
        const val RELATIVE_GATE_LU = 10.0
        const val SUB_BLOCK_COUNT = 4
        const val SUB_BLOCKS_PER_SECOND = 10
        const val INV_FULL_SCALE = 1.0 / 32767.0

        fun lufsToMeanSquare(lufs: Double): Double = 10.0.pow((lufs - LUFS_OFFSET) / 10.0)

        fun meanSquareToLufs(meanSquare: Double): Double = LUFS_OFFSET + 10.0 * log10(meanSquare)
    }
}

/** The per-sample direct-form-I biquad, over a production [Biquad]'s coefficients. */
internal class PerSampleBiquad(design: Biquad) {
    private val b0 = design.b0
    private val b1 = design.b1
    private val b2 = design.b2
    private val a1 = design.a1
    private val a2 = design.a2
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
}

/** The original modulo-indexed per-sample true-peak walk, taking the coefficients in. */
internal class ModuloTruePeak(channels: Int, private val coefficients: Array<DoubleArray>) {
    private val taps = coefficients[0].size
    private val history = Array(channels) { DoubleArray(taps) }
    private val historyPos = IntArray(channels)
    var maxAbs = 0.0

    fun process(channel: Int, sample: Double) {
        val a = abs(sample)
        if (a > maxAbs) maxAbs = a
        val hist = history[channel]
        val pos = historyPos[channel]
        hist[pos] = sample
        for (co in coefficients) {
            var sum = 0.0
            for (t in 0 until taps) {
                sum += hist[((pos - t) + taps) % taps] * co[t]
            }
            val mag = abs(sum)
            if (mag > maxAbs) maxAbs = mag
        }
        historyPos[channel] = (pos + 1) % taps
    }
}
