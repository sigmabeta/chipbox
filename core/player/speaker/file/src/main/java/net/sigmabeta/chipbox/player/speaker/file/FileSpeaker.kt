package net.sigmabeta.chipbox.player.speaker.file

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import net.sigmabeta.chipbox.player.common.BYTES_PER_SAMPLE
import net.sigmabeta.chipbox.player.common.CHANNELS_STEREO
import net.sigmabeta.chipbox.player.common.GeneratorEvent
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FileSpeaker(
    private val sampleRate: Int,
    private val bufferSizeBytes: Int,
    private val externalStorageDir: File,
    private val generator: Generator,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Speaker {
    private val speakerScope = CoroutineScope(dispatcher)

    private var ongoingPlaybackJob: Job? = null

    private var bytesWritten = 0

    override suspend fun play(trackId: Long) {
        ongoingPlaybackJob?.cancelAndJoin()
        ongoingPlaybackJob = speakerScope.launch {
            val file = initializeOutputStream(sampleRate, externalStorageDir)
            if (file != null) {
                startPlayback(file, trackId)
            }
        }
    }

    private suspend fun startPlayback(output: OutputStream, trackId: Long) {
        generator
            .audioStream(trackId)
            .collect {
                when (it) {
                    GeneratorEvent.Complete -> onPlaybackComplete(output)
                    GeneratorEvent.Error -> onPlaybackError(output)
                    is GeneratorEvent.Audio -> {
                        onAudioGenerated(it, output)
                        return@collect
                    }
                }

                ongoingPlaybackJob?.cancel()
            }
    }

    private fun onPlaybackComplete(output: OutputStream) {
        Timber.d("Generator reports track complete.")
        teardown(output)
    }

    private fun onPlaybackError(output: OutputStream) {
        Timber.e("Generator reports playback error.")
        teardown(output)
    }

    private fun onAudioGenerated(audio: GeneratorEvent.Audio, output: OutputStream) {
        try {
            val audioAsBytes = audio.data.toByteArray()
            output.write(audioAsBytes)
            bytesWritten += audioAsBytes.size
        } catch (ex: Exception) {
            logProblems(ex.message)
        }
    }

    private fun teardown(output: OutputStream) {
        Timber.i("Tearing down output file.")
        output.close()

        writeSizeToHeader()
    }

    private fun initializeOutputStream(
        sampleRate: Int,
        externalStorageDir: File
    ): OutputStream? {
        return try {
            val outputFile = getOutputFile(externalStorageDir)
            val fileOutput = outputFile.outputStream()
            val bufferedOutput = BufferedOutputStream(fileOutput)

            writeHeader(bufferedOutput, sampleRate)
            bufferedOutput

        } catch (ex: Exception) {
            logProblems(ex.message)
            null
        }
    }

    private fun getOutputFile(externalStorageDir: File): File {
        var outputFolder = File(externalStorageDir, FOLDER_NAME)

        if (outputFolder.exists()) {
            if (!outputFolder.isDirectory) {
                outputFolder = File(externalStorageDir, "$FOLDER_NAME 1")

                if (outputFolder.exists()) {
                    if (!outputFolder.isDirectory) {
                        throw IllegalStateException("You really don't want this thing to work, huh.")
                    }
                } else {
                    outputFolder.mkdir()
                }
            }
        } else {
            val success = outputFolder.mkdir()
            if (!success) {
                throw IllegalStateException("Failed to create folder.")
            }
        }

        val file = File(outputFolder, "temp.wav")
        file.createNewFile()

        return file
    }

    private fun writeHeader(output: OutputStream, sampleRate: Int) {
        writeWaveHeader(output)

        output.write(HEADER_STRING_FMT.toByteArray())
        writeIntLittleEndian(output, HEADER_SIZE_CHUNK_FMT)
        writeShortLittleEndian(output, HEADER_BYTE_FMT_PCM.toShort())
        writeShortLittleEndian(output, CHANNELS_STEREO.toShort()) // 2 bytes

        writeIntLittleEndian(output, sampleRate) // 4 bytes
        writeIntLittleEndian(output, sampleRate * CHANNELS_STEREO * BYTES_PER_SAMPLE) // 4 bytes
        writeShortLittleEndian(output, (CHANNELS_STEREO * BYTES_PER_SAMPLE).toShort()) // 2 bytes
        writeShortLittleEndian(output, (BYTES_PER_SAMPLE * 8).toShort()) // 2 bytes

        output.write(HEADER_STRING_DATA.toByteArray())
    }


    private fun writeWaveHeader(output: OutputStream) {
        val emptySizeValue = byteArrayOf(0, 0, 0, 0)

        output.write(HEADER_STRING_RIFF.toByteArray())
        output.write(emptySizeValue)
        output.write(HEADER_STRING_WAVE.toByteArray())
    }

    private fun writeSizeToHeader() {
        val file = getOutputFile(externalStorageDir)
        val seekableFile = RandomAccessFile(file, MODE_FILE_ACCESS_RW)

        // TODO Pretty sure this assumes little-endianness. Maybe a bad idea?
        seekableFile.seek(4)
        seekableFile.writeInt(bytesWritten + HEADER_SIZE_TOTAL)

        seekableFile.seek(0x40)
        seekableFile.writeInt(bytesWritten)

        Timber.i("Wrote $bytesWritten bytes of audio to file.")
    }

    private fun writeShortLittleEndian(output: OutputStream, short: Short) {
        val bb: ByteBuffer = ByteBuffer.allocate(2)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        bb.putShort(short)

        bb.flip()
        val array = bb.array()
        output.write(array)
    }

    private fun writeIntLittleEndian(output: OutputStream, int: Int) {
        val bb: ByteBuffer = ByteBuffer.allocate(4)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(int)

        bb.flip()
        val array = bb.array()
        output.write(array)
    }

    private fun logProblems(message: String?) {
        Timber.e("Error writing to file: $message")
    }

    private fun Short.toBytes(): ByteArray {
        return byteArrayOf(
            (toInt() and 0x00FF).toByte(),
            ((toInt() and 0xFF00) shr (8)).toByte()
        )
    }

    private fun ShortArray.toByteArray(): ByteArray {
        return this
            .map { it.toBytes().toList() }
            .flatten()
            .toByteArray()
    }

    companion object {
        const val FOLDER_NAME = "Chipbox Output Files"

        const val HEADER_STRING_RIFF = "RIFF"
        const val HEADER_STRING_WAVE = "WAVE"
        const val HEADER_STRING_FMT = "fmt" + 0x20.toChar()
        const val HEADER_STRING_DATA = "data"

        const val HEADER_BYTE_FMT_PCM = 1

        const val HEADER_SIZE_CHUNK_FMT = 16
        const val HEADER_SIZE_TOTAL = 36

        const val MODE_FILE_ACCESS_RW = "rw"
    }
}


