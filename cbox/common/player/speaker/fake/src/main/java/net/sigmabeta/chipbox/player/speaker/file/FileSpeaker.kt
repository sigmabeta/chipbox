package net.sigmabeta.chipbox.player.speaker.file

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.common.BYTES_PER_SAMPLE
import net.sigmabeta.chipbox.player.common.CHANNELS_STEREO
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.sage.logging.Hatchet
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * [Speaker] that writes incoming PCM to a WAV file under [externalStorageDir]. Used for
 * exporting an emulator's output rather than playing it back — same pipeline, different sink.
 *
 * The WAV header is written up front with placeholder size fields; the real RIFF / data chunk
 * sizes are patched into the header in [teardown] once the total byte count is known. The file
 * is reopened on any sample-rate change, since the WAV format encodes a single rate per file.
 */
class FileSpeaker(
        private val externalStorageDir: File,
        hatchet: Hatchet,
        bufferManager: ConsumerBufferManager,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Speaker(bufferManager, hatchet, dispatcher) {
    private var bytesWritten = 0

    private var file: OutputStream? = null

    private var trackSampleRate: Int = 0

    override fun onAudioReceived(audio: AudioBuffer) {
        if (file == null || trackSampleRate != audio.sampleRate) {
            teardown()

            file = initializeOutputStream(audio.sampleRate, externalStorageDir)
            trackSampleRate = audio.sampleRate
        }

        try {
            val audioAsBytes = audio.data.toByteArray()

            hatchet.d("Writing ${audioAsBytes.size} bytes to output...")

            file?.write(audioAsBytes)
            bytesWritten += audioAsBytes.size
        } catch (ex: Exception) {
            logProblems(ex.message)
        }
    }

    override fun teardown() {
        hatchet.d("Tearing down output file.")

        file?.close()

        if (bytesWritten > 0) {
            writeSizeToHeader()
            bytesWritten = 0
        }
    }

    private fun initializeOutputStream(
        sampleRate: Int,
        externalStorageDir: File
    ): OutputStream? = try {
            val outputFile = getOutputFile(externalStorageDir)
            val fileOutput = outputFile.outputStream()
            val bufferedOutput = BufferedOutputStream(fileOutput)

            writeHeader(bufferedOutput, sampleRate)
            bufferedOutput
        } catch (ex: Exception) {
            logProblems(ex.message)
            null
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
        writeShortLittleEndian(output, (BYTES_PER_SAMPLE * BITS_PER_BYTE).toShort()) // 2 bytes

        output.write(HEADER_STRING_DATA.toByteArray())
        output.write(byteArrayOf(0, 0, 0, 0)) // data chunk size placeholder, patched in teardown
    }

    private fun writeWaveHeader(output: OutputStream) {
        val emptySizeValue = byteArrayOf(0, 0, 0, 0)

        output.write(HEADER_STRING_RIFF.toByteArray())
        output.write(emptySizeValue)
        output.write(HEADER_STRING_WAVE.toByteArray())
    }

    private fun writeSizeToHeader() {
        val file = getOutputFile(externalStorageDir)
        RandomAccessFile(file, MODE_FILE_ACCESS_RW).use { seekableFile ->
            seekableFile.seek(HEADER_OFFSET_RIFF_SIZE)
            seekableFile.write(intToLittleEndianBytes(bytesWritten + HEADER_SIZE_TOTAL))

            seekableFile.seek(HEADER_OFFSET_DATA_SIZE)
            seekableFile.write(intToLittleEndianBytes(bytesWritten))
        }

        hatchet.d("Wrote $bytesWritten bytes of audio to file.")
    }

    private fun intToLittleEndianBytes(value: Int): ByteArray =
        ByteBuffer.allocate(BYTES_PER_INT).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun writeShortLittleEndian(output: OutputStream, short: Short) {
        val bb: ByteBuffer = ByteBuffer.allocate(2)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        bb.putShort(short)

        bb.flip()
        val array = bb.array()
        output.write(array)
    }

    private fun writeIntLittleEndian(output: OutputStream, int: Int) {
        val bb: ByteBuffer = ByteBuffer.allocate(BYTES_PER_INT)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(int)

        bb.flip()
        val array = bb.array()
        output.write(array)
    }

    private fun logProblems(message: String?) {
        hatchet.e("Error writing to file: $message")
    }

    private fun Short.toBytes(): ByteArray = byteArrayOf(
            (toInt() and LOW_BYTE_MASK).toByte(),
            ((toInt() and HIGH_BYTE_MASK) shr (BITS_PER_BYTE)).toByte()
        )

    private fun ShortArray.toByteArray(): ByteArray = this
            .map { it.toBytes().toList() }
            .flatten()
            .toByteArray()

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

        /** Byte offset of the RIFF chunk size field, patched in once the body size is known. */
        private const val HEADER_OFFSET_RIFF_SIZE = 4L

        /** Byte offset of the data chunk size field within the WAV header. */
        private const val HEADER_OFFSET_DATA_SIZE = 40L

        /** Bits per byte; WAV stores bits-per-sample as bytes-per-sample * 8. */
        private const val BITS_PER_BYTE = 8

        /** Byte width of a 32-bit int written little-endian into the header. */
        private const val BYTES_PER_INT = 4

        /** Masks isolating the low / high byte of a 16-bit sample. */
        private const val LOW_BYTE_MASK = 0x00FF
        private const val HIGH_BYTE_MASK = 0xFF00
    }
}
