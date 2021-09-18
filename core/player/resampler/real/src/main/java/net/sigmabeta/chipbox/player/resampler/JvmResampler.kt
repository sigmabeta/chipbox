package net.sigmabeta.chipbox.player.resampler

import com.laszlosystems.libresample4j.Resampler
import net.sigmabeta.chipbox.player.common.isDivisibleBy

class JvmResampler(val outputLengthShorts: Int, inputSampleRate: Int, outputSampleRate: Int) {
    private val resampler: Resampler

    private val factor: Double

    private val inputFloatsLeft: FloatArray
    private val inputFloatsRight: FloatArray

    private val outputFloatsLeft: FloatArray
    private val outputFloatsRight: FloatArray

    private val output: ShortArray

    val inputLengthShorts = outputLengthShorts * inputSampleRate / outputSampleRate

    init {
        inputFloatsLeft = FloatArray(inputLengthShorts / 2)
        inputFloatsRight = FloatArray(inputLengthShorts / 2)
        outputFloatsLeft = FloatArray(outputLengthShorts / 2)
        outputFloatsRight = FloatArray(outputLengthShorts / 2)
        output = ShortArray(outputLengthShorts)

        factor = outputSampleRate.toDouble() / inputSampleRate

        println("Resampler: Buffer size in / out: ${inputLengthShorts * 2} bytes / ${outputLengthShorts * 2} bytes")
        println("Resampler: Sample rate in / out: $inputSampleRate / $outputSampleRate")
        println("Resampler: Factor: $factor")

        resampler = Resampler(
            false,
            factor,
            factor
        )
    }

    fun resample(audio: ShortArray): ShortArray {
        if (factor == 1.0) {
            return audio
        }

        inputFloatsLeft.clear()
        inputFloatsRight.clear()

        outputFloatsLeft.clear()
        outputFloatsRight.clear()

        output.clear()

        splitInputs(audio, inputFloatsLeft, inputFloatsRight)

        resampleOneChannel(inputFloatsLeft, outputFloatsLeft)
        resampleOneChannel(inputFloatsRight, outputFloatsRight)

        mergeOutputs(output, outputFloatsLeft, outputFloatsRight)
        return output
    }

    private fun splitInputs(
        audio: ShortArray,
        left: FloatArray,
        right: FloatArray
    ) {
        for (index in left.indices) {
            val inputIndex = index * 2

            val leftAsShort = audio[inputIndex]
            val rightAsShort = audio[inputIndex + 1]

            left[index] = leftAsShort / Short.MAX_VALUE.toFloat()
            right[index] = rightAsShort / Short.MAX_VALUE.toFloat()
        }
    }

    private fun mergeOutputs(
        output: ShortArray,
        left: FloatArray,
        right: FloatArray
    ) {
        for (index in left.indices) {
            val outputIndex = index * 2

            output[outputIndex] = (left[index] * Short.MAX_VALUE).toInt().toShort()
            output[outputIndex + 1] = (right[index] * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun resampleOneChannel(
        inputFloats: FloatArray,
        outputFloats: FloatArray
    ) {
        outputFloats.clear()

        resampler.process(
            factor,
            inputFloats,
            0,
            inputFloats.size,
            false,
            outputFloats,
            0,
            outputFloats.size
        )

        for (index in outputFloats.indices) {
            val outputFloat = outputFloats[index]
            output[index] = (outputFloat * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun FloatArray.clear() {
        for (index in indices) {
            set(index, 0.0f)
        }
    }

    private fun ShortArray.clear() {
        for (index in indices) {
            set(index, 0)
        }
    }
}