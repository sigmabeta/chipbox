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

        println("Resampler: Out buffer size: ${outputLengthShorts * 2} bytes")
        println("Resampler: In float buffer size: ${inputLengthShorts * 2} bytes")

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
        audio.forEachIndexed { index, valueAsShort ->
            if (index.isDivisibleBy(2)) {
                left[index / 2] = valueAsShort / Short.MAX_VALUE.toFloat()
            } else {
                right[index / 2] = valueAsShort / Short.MAX_VALUE.toFloat()
            }
        }
    }

    private fun mergeOutputs(
        output: ShortArray,
        left: FloatArray,
        right: FloatArray
    ) {
        output.forEachIndexed { index, valueAsShort ->
            if (index.isDivisibleBy(2)) {
                output[index] = (left[index / 2] * Short.MAX_VALUE).toInt().toShort()
            } else {
                output[index] = (right[index / 2] * Short.MAX_VALUE).toInt().toShort()
            }
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

        outputFloats.forEachIndexed { index, outputFloat ->
            output[index] = (outputFloat * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun FloatArray.clear() {
        forEachIndexed { index, _ ->
            set(index, 0.0f)
        }
    }
}