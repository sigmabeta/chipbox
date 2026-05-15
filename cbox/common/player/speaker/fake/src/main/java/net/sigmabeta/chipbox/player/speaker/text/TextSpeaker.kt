package net.sigmabeta.chipbox.player.speaker.text

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.sage.logging.Hatchet

/**
 * Debug-only [Speaker] that prints each incoming buffer as a `Frame | Left | Right` table via
 * [Hatchet]. Useful for verifying that the producer side is generating sensible samples without
 * needing audio hardware.
 */
class TextSpeaker(
    hatchet: Hatchet,
        bufferManager: ConsumerBufferManager,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Speaker(bufferManager, hatchet, dispatcher)  {
    override fun onAudioReceived(audio: AudioBuffer) {
        logBuffer(audio)
    }

    override fun teardown() = Unit

    private fun logBuffer(audio: AudioBuffer) {
        hatchet.d(audio.toReadableString())
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


