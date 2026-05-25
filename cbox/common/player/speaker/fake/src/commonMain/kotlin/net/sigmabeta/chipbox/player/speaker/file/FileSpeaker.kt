package net.sigmabeta.chipbox.player.speaker.file

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.player.buffer.AudioBuffer
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.common.BYTES_PER_SAMPLE
import net.sigmabeta.chipbox.player.common.CHANNELS_STEREO
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.sage.logging.Hatchet
import okio.Buffer
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.buffer

/**
 * [Speaker] that writes incoming PCM to a WAV file under [externalStorageDir]. Used for
 * exporting an emulator's output rather than playing it back — same pipeline, different sink.
 *
 * The WAV header is written up front with placeholder size fields; the real RIFF / data chunk
 * sizes are patched into the header in [teardown] once the total byte count is known (via a
 * positioned write through an okio [okio.FileHandle]). The file is reopened on any sample-rate
 * change, since the WAV format encodes a single rate per file.
 */
class FileSpeaker(
    private val externalStorageDir: Path,
    private val fileSystem: FileSystem,
    hatchet: Hatchet,
    bufferManager: ConsumerBufferManager,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : Speaker(bufferManager, hatchet, dispatcher) {
    private var bytesWritten = 0

    private var sink: BufferedSink? = null

    private var trackSampleRate: Int = 0

    override fun onAudioReceived(audio: AudioBuffer) {
        if (sink == null || trackSampleRate != audio.sampleRate) {
            teardown()

            sink = initializeSink(audio.sampleRate)
            trackSampleRate = audio.sampleRate
        }

        try {
            val audioAsBytes = audio.data.toByteArray()

            hatchet.d("Writing ${audioAsBytes.size} bytes to output...")

            sink?.write(audioAsBytes)
            bytesWritten += audioAsBytes.size
        } catch (ex: Exception) {
            logProblems(ex.message)
        }
    }

    override fun teardown() {
        hatchet.d("Tearing down output file.")

        sink?.close()
        sink = null

        if (bytesWritten > 0) {
            writeSizeToHeader()
            bytesWritten = 0
        }
    }

    private fun initializeSink(sampleRate: Int): BufferedSink? = try {
        val outputFile = getOutputFile()
        val bufferedOutput = fileSystem.sink(outputFile).buffer()

        writeHeader(bufferedOutput, sampleRate)
        bufferedOutput
    } catch (ex: Exception) {
        logProblems(ex.message)
        null
    }

    private fun getOutputFile(): Path {
        var outputFolder = externalStorageDir / FOLDER_NAME

        val meta = fileSystem.metadataOrNull(outputFolder)
        if (meta != null) {
            if (!meta.isDirectory) {
                outputFolder = externalStorageDir / "$FOLDER_NAME 1"

                val fallbackMeta = fileSystem.metadataOrNull(outputFolder)
                if (fallbackMeta != null) {
                    if (!fallbackMeta.isDirectory) {
                        throw IllegalStateException("You really don't want this thing to work, huh.")
                    }
                } else {
                    fileSystem.createDirectories(outputFolder)
                }
            }
        } else {
            fileSystem.createDirectories(outputFolder)
        }

        return outputFolder / "temp.wav"
    }

    private fun writeHeader(output: BufferedSink, sampleRate: Int) {
        output.writeUtf8(HEADER_STRING_RIFF)
        output.writeIntLe(0) // RIFF chunk size placeholder, patched in teardown
        output.writeUtf8(HEADER_STRING_WAVE)

        output.writeUtf8(HEADER_STRING_FMT)
        output.writeIntLe(HEADER_SIZE_CHUNK_FMT)
        output.writeShortLe(HEADER_BYTE_FMT_PCM)
        output.writeShortLe(CHANNELS_STEREO) // 2 bytes

        output.writeIntLe(sampleRate) // 4 bytes
        output.writeIntLe(sampleRate * CHANNELS_STEREO * BYTES_PER_SAMPLE) // 4 bytes
        output.writeShortLe(CHANNELS_STEREO * BYTES_PER_SAMPLE) // 2 bytes
        output.writeShortLe(BYTES_PER_SAMPLE * BITS_PER_BYTE) // 2 bytes

        output.writeUtf8(HEADER_STRING_DATA)
        output.writeIntLe(0) // data chunk size placeholder, patched in teardown
    }

    private fun writeSizeToHeader() {
        val outputFile = getOutputFile()
        val handle = fileSystem.openReadWrite(outputFile)
        try {
            handle.write(HEADER_OFFSET_RIFF_SIZE, intLittleEndian(bytesWritten + HEADER_SIZE_TOTAL), 0, BYTES_PER_INT)
            handle.write(HEADER_OFFSET_DATA_SIZE, intLittleEndian(bytesWritten), 0, BYTES_PER_INT)
        } finally {
            handle.close()
        }

        hatchet.d("Wrote $bytesWritten bytes of audio to file.")
    }

    private fun intLittleEndian(value: Int): ByteArray = Buffer().writeIntLe(value).readByteArray()

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
        const val HEADER_STRING_FMT = "fmt " // trailing 0x20
        const val HEADER_STRING_DATA = "data"

        const val HEADER_BYTE_FMT_PCM = 1

        const val HEADER_SIZE_CHUNK_FMT = 16
        const val HEADER_SIZE_TOTAL = 36

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
