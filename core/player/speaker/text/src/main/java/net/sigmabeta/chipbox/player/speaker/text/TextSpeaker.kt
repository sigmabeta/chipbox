package net.sigmabeta.chipbox.player.speaker.text

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import net.sigmabeta.chipbox.player.common.GeneratorEvent
import net.sigmabeta.chipbox.player.common.framesToMillis
import net.sigmabeta.chipbox.player.common.millisToSeconds
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker
import timber.log.Timber

class TextSpeaker(
    private val generator: Generator,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Speaker {
    private val speakerScope = CoroutineScope(dispatcher)

    private var ongoingPlaybackJob: Job? = null

    private var lastBufferPrinted = 0
    override suspend fun play(trackId: Long) {
        Timber.w("${Thread.currentThread().name}; Received play command for id $trackId")
        ongoingPlaybackJob?.cancelAndJoin()
        ongoingPlaybackJob = speakerScope.launch {
            Timber.w("${Thread.currentThread().name}; Begin playback for id $trackId")
            playAudioFromGenerator(trackId)
            Timber.w("${Thread.currentThread().name}; End playback for id $trackId")
        }
    }

    private suspend fun playAudioFromGenerator(trackId: Long) {
        generator
            .audioStream(trackId, 44100, 2048)
            .collect {
                when (it) {
                    GeneratorEvent.Loading -> onPlaybackLoading()
                    GeneratorEvent.Complete -> onPlaybackComplete()
                    is GeneratorEvent.Error -> onPlaybackError(it.message)
                    is GeneratorEvent.Audio -> logBuffer(it)
                }
            }
    }

    private fun onPlaybackLoading() {
        Timber.d("Generator reports track loading.")
    }

    private fun onPlaybackComplete() {
        Timber.d("Generator reports track complete.")
    }

    private fun onPlaybackError(message: String) {
        Timber.e("Generator reports playback error: $message")
    }

    private fun logBuffer(audio: GeneratorEvent.Audio) {
        val diff = audio.bufferNumber - lastBufferPrinted
        if (diff != 1) {
            Timber.e("Last buffer printed was $lastBufferPrinted, this one is ${audio.bufferNumber}. Difference is $diff")
        }

        lastBufferPrinted = audio.bufferNumber
        Timber.i(audio.toReadableString())
    }

    private fun GeneratorEvent.Audio.toReadableString(): String {
        val toString = StringBuilder().also {
            val headerRow = "Frame | Time (s) | Sample # |  Left  |  Right |"

            it.append("${Thread.currentThread().name}; Outputting frames from buffer #$bufferNumber:")
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

            it.append("${Thread.currentThread().name}; End buffer #$bufferNumber")
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


