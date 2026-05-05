package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import timber.log.Timber
import java.io.UnsupportedEncodingException

object NsfReader : Reader() {
    @OptIn(ExperimentalStdlibApi::class)
    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? {
        try {
            val fileAsByteBuffer = bytesAsByteBuffer(bytes)

            val formatHeader = fileAsByteBuffer.nextBytesAsString(4)
            if (formatHeader == null) {
                Timber.e("No header found.")
                return null
            }

            if (!isNsfFile(formatHeader)) {
                Timber.e("NSF header missing.")
                return null
            }

            val numberOfTracks = getNumberOfTracks(bytes)
            val gameTitle = getGameTitle(bytes)
            val gameArtist = getGameArtist(bytes)

            val tracks = mutableListOf<RawTrack>()

            for (index in 0 until numberOfTracks) {
                tracks.add(
                    RawTrack(
                        identifier,
                        "",
                        TAG_UNKNOWN,
                        gameArtist.orUnknown(),
                        gameTitle,
                        LENGTH_UNKNOWN_MS,
                        index,
                        true
                    )
                )
            }
            return tracks
        } catch (iae: IllegalArgumentException) {
            Timber.e("Illegal argument: ${iae.message}")
            return null
        } catch (e: UnsupportedEncodingException) {
            Timber.e("Unsupported Encoding: ${e.message}")
            return null
        }
    }

    private fun getNumberOfTracks(fileAsBytes: ByteArray): Int {
        return fileAsBytes[0x06].toInt()
    }

    private fun getGameTitle(fileAsBytes: ByteArray): String {
        return try {
            fileAsBytes
                .decodeToString(0x0E, 0x2E, true)
                .trim()
        } catch (ex: Exception) {
            Timber.e("Unable to read game title: ${ex.message}")
            TAG_UNKNOWN
        }
    }

    private fun getGameArtist(fileAsBytes: ByteArray): String {
        return try {
            fileAsBytes
                .decodeToString(0x2E, 0x4E, true)
                .trim()
        } catch (ex: Exception) {
            Timber.e("Unable to read game title: ${ex.message}")
            TAG_UNKNOWN
        }
    }

    private fun isNsfFile(header: String) = header.contentEquals(HEADER_MAGIC)

    private const val HEADER_MAGIC = "NESM"
}
