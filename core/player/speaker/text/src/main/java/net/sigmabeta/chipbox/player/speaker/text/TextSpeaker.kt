package net.sigmabeta.chipbox.player.speaker.text

import net.sigmabeta.chipbox.player.common.AudioBuffer
import net.sigmabeta.chipbox.player.common.framesToMillis
import net.sigmabeta.chipbox.player.common.millisToSeconds
import net.sigmabeta.chipbox.player.speaker.Speaker
import timber.log.Timber

class TextSpeaker() : Speaker {
    override fun play(audio: AudioBuffer) {
        Timber.i(audio.toReadableString())
    }

    private fun AudioBuffer.toReadableString(): String {
        val toString = StringBuilder().also {
            val headerRow = "Frame | Time (s) | Sample # |  Left  |  Right |\n"

            it.append("Outputting frames: \n")
            it.append(headerRow)
            it.append(headerRow.headerToDivider())

            for (frameCount in 0 until data.size / 2) {
                val leftSampleIndex = frameCount * 2
                val rightSampleIndex = leftSampleIndex + 1

                val millisOffset = frameCount.toLong().framesToMillis(sampleRate)

                it.append(String.format("%4d:", frameCount))
                it.append(SEPARATOR_DATA_COLUMN)

                it.append(String.format("%8f", (timestampMillis + millisOffset).millisToSeconds()))
                it.append(SEPARATOR_DATA_COLUMN)

                it.append(String.format("%8d", leftSampleIndex))
                it.append(SEPARATOR_DATA_COLUMN)

                it.append(String.format("%6d", data[leftSampleIndex]))
                it.append(SEPARATOR_DATA_COLUMN)

                it.append(String.format("%6d", data[rightSampleIndex]))
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
    }
}


