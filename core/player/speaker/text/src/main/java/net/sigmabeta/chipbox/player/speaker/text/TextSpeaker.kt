package net.sigmabeta.chipbox.player.speaker.text

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.common.framesToMillis
import net.sigmabeta.chipbox.player.speaker.Speaker

class TextSpeaker(
        bufferManager: ConsumerBufferManager,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Speaker(bufferManager, dispatcher)  {
    override fun onAudioReceived(audio: AudioBuffer) {
        logBuffer(audio)
    }

    override fun teardown() = Unit

    private fun logBuffer(audio: AudioBuffer) {
        println(audio.toReadableString())
    }

    private fun AudioBuffer.toReadableString(): String {
        val toString = StringBuilder().also {
            val headerRow = "Frame |  Left  |  Right |"

            it.append("${Thread.currentThread().name}; Outputting frames from buffer:")
            it.append("\n")
            it.append(headerRow)
            it.append("\n")
            it.append(headerRow.headerToDivider())

            for (frameCount in 0 until data.size / 2) {
                val leftSampleIndex = frameCount * 2
                val rightSampleIndex = leftSampleIndex + 1

                val millisOffset = frameCount.framesToMillis(sampleRate)

                it.append(String.format("%4d:", frameCount))
                it.append(SEPARATOR_DATA_COLUMN)

                it.append(String.format("%6d", data[leftSampleIndex]))
                it.append(SEPARATOR_DATA_COLUMN)

                it.append(String.format("%6d", data[rightSampleIndex]))
                it.append(SEPARATOR_DATA_COLUMN)

                it.append("\n")
            }

            it.append("${Thread.currentThread().name}; End buffer.")
        }.toString()
        return toString
    }

    private fun String.headerToDivider() = map { "-" }
        .joinToString("")
        .replaceRange(length - 1, length, "\n")

    companion object {
        const val SEPARATOR_DATA_COLUMN = " | "
    }
}


