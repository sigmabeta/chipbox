package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import timber.log.Timber
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer

object SpcReader : Reader() {
    override fun readTracksFromFile(path: String): List<RawTrack>? {
        try {
            val fileAsByteBuffer = fileAsByteBuffer(path)
            val formatHeader = fileAsByteBuffer.nextBytesAsString(33)
            if (formatHeader == null) {
                Timber.e("No header found.")
                return null
            }

            if (!isSpcFile(formatHeader)) {
                Timber.e("SPC header missing.")
                return null
            }

            val spcMainTag = readMainTag(fileAsByteBuffer)
            fileAsByteBuffer.position(0x1_0200)

            if (spcMainTag == null) {
                return null
            }

            return listOf(
                RawTrack(
                    path,
                    spcMainTag.songTitle,
                    spcMainTag.artistName,
                    spcMainTag.gameTitle,
                    spcMainTag.trackLengthSeconds * 1_000L,
                    0,
                    spcMainTag.fadeLengthMillis != 0L
                )
            )
        } catch (iae: IllegalArgumentException) {
            Timber.e("Illegal argument: ${iae.message}")
            return null
        } catch (e: UnsupportedEncodingException) {
            Timber.e("Unsupported Encoding: ${e.message}")
            return null
        } catch (e: Exception) {
            Timber.e("Error reading $path: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun readMainTag(fileAsByteBuffer: ByteBuffer): SpcMainTag? {
        val hasHeaderInfo = fileAsByteBuffer
            .nextBytes(3)
            ?.last()
            ?.equals(0x1A.toByte()) ?: false

        if (!hasHeaderInfo) {
            Timber.e("File has no metadata.")
            return null
        }

        // These must all happen in order.
        val minorVersion = fileAsByteBuffer.nextBytesAsInt(1)
        val spcRegistersIgnored = fileAsByteBuffer.nextBytes(LENGTH_SPC_REGISTERS)
        val songTitle = fileAsByteBuffer.nextBytesAsString(LENGTH_TAG_STANDARD)
        val gameTitle = fileAsByteBuffer.nextBytesAsString(LENGTH_TAG_STANDARD)
        val dumperName = fileAsByteBuffer.nextBytesAsString(LENGTH_TAG_DUMPER_NAME)
        val comments = fileAsByteBuffer.nextBytesAsString(LENGTH_TAG_STANDARD)
        val dumpDate = fileAsByteBuffer.nextBytesAsString(LENGTH_TAG_DUMP_DATE)
        val lengthSecondsString = fileAsByteBuffer.nextBytesAsString(LENGTH_TAG_TRACK_LENGTH)
        val fadeLengthMillisString = fileAsByteBuffer.nextBytesAsString(LENGTH_TAG_FADE_LENGTH)
        val artistName = fileAsByteBuffer.nextBytesAsString(LENGTH_TAG_STANDARD)

        if (SHOULD_LOG_EXTRA_INFO) {
            Timber.i("SPC Minor Ver: $minorVersion")
            Timber.i("SPC Registers: $spcRegistersIgnored")
            Timber.i("Dumper: $dumperName")
            Timber.i("Dump Date: $comments")
            Timber.i("Comments: $dumpDate")
        }

        // SPC lengths are stored as string-encoded numbers?!?!? Apparently this is supposed to not
        // always be the case, but I've never seen an example of it not being the case, so until then,
        // this is assumed to be how it works.
        val lengthSeconds = lengthSecondsString?.toLongOrNull() ?: (LENGTH_UNKNOWN_MS / 1_000L)
        val fadeLengthMillis =
            fadeLengthMillisString?.toLongOrNull() ?: (LENGTH_UNKNOWN_MS / 1_000L)

        return SpcMainTag(
            songTitle.orUnknown(),
            gameTitle.orUnknown(),
            lengthSeconds,
            fadeLengthMillis,
            artistName.orUnknown()
        )
    }


    private fun isSpcFile(header: String) =
        header.contentEquals("SNES-SPC700 Sound File Data v0.30")

    private const val SHOULD_LOG_EXTRA_INFO = false

    private const val LENGTH_SPC_REGISTERS = 9
    private const val LENGTH_TAG_STANDARD = 32
    private const val LENGTH_TAG_DUMPER_NAME = 16
    private const val LENGTH_TAG_DUMP_DATE = 11
    private const val LENGTH_TAG_TRACK_LENGTH = 3
    private const val LENGTH_TAG_FADE_LENGTH = 5
}

data class SpcMainTag(
    val songTitle: String,
    val gameTitle: String,
    val trackLengthSeconds: Long,
    val fadeLengthMillis: Long,
    val artistName: String
)