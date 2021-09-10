package net.sigmabeta.chipbox.player.resampler

import com.laszlosystems.libresample4j.SampleBuffers

class ResampleBuffer(private val output: FloatArray, input: ShortArray) : SampleBuffers {
    private val input: FloatArray = FloatArray(input.size)

    private var inputsUsed = 0

    private var outputsUsed = 0

    fun feedInput(inputShorts: ShortArray) {
        inputsUsed = 0
        outputsUsed = 0

        val source = input
        inputShorts.forEachIndexed { index, inputShort ->
            source[index] = inputShort / Short.MAX_VALUE.toFloat()
        }
    }

    override fun getInputBufferLength() = input.size - inputsUsed

    override fun getOutputBufferLength() = output.size - outputsUsed

    override fun produceInput(array: FloatArray, offset: Int, length: Int) {
        val source = input
        source.copyInto(array, offset, inputsUsed, inputsUsed + length)
        inputsUsed += length
    }

    override fun consumeOutput(array: FloatArray, offset: Int, length: Int) {
        val target = output
        array.copyInto(target, outputsUsed, offset, offset + length)
        outputsUsed += length
    }
}