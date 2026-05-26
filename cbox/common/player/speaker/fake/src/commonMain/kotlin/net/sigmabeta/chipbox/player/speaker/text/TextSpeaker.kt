package net.sigmabeta.chipbox.player.speaker.text

import kotlinx.coroutines.CoroutineDispatcher
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.speaker.BaseSpeaker
import net.sigmabeta.chipbox.utils.ioDispatcher
import net.sigmabeta.sage.logging.Hatchet

/**
 * Debug-only [Speaker] that prints each incoming buffer as a `Frame | Left | Right` table via
 * [Hatchet]. Useful for verifying that the producer side is generating sensible samples without
 * needing audio hardware.
 */
class TextSpeaker(
    hatchet: Hatchet,
        bufferManager: ConsumerBufferManager,
        dispatcher: CoroutineDispatcher = ioDispatcher
) : BaseSpeaker(bufferManager, hatchet, dispatcher) {
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

            it.append("Outputting frames from buffer:")
            it.append("\n")
            it.append(headerRow)
            it.append("\n")
            it.append(headerRow.headerToDivider())

            for (frameCount in 0 until data.size / 2) {
                val leftSampleIndex = frameCount * 2
                val rightSampleIndex = leftSampleIndex + 1

                it.append(frameCount.toString().padStart(FRAME_COLUMN_WIDTH) + ":")
                it.append(SEPARATOR_DATA_COLUMN)

                it.append(data[leftSampleIndex].toString().padStart(SAMPLE_COLUMN_WIDTH))
                it.append(SEPARATOR_DATA_COLUMN)

                it.append(data[rightSampleIndex].toString().padStart(SAMPLE_COLUMN_WIDTH))
                it.append(SEPARATOR_DATA_COLUMN)

                it.append("\n")
            }

            it.append("End buffer.")
        }.toString()
        return toString
    }

    private fun String.headerToDivider() = map { "-" }
        .joinToString("")
        .replaceRange(length - 1, length, "\n")

    companion object {
        const val SEPARATOR_DATA_COLUMN = " | "

        /** Right-justified column widths, replacing the old String.format("%4d")/("%6d"). */
        private const val FRAME_COLUMN_WIDTH = 4
        private const val SAMPLE_COLUMN_WIDTH = 6
    }
}
