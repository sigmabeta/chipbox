package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import timber.log.Timber
import java.io.File
import java.io.UnsupportedEncodingException

object NsfReader : Reader() {
    @OptIn(ExperimentalStdlibApi::class)
    override fun readTracksFromFile(path: String): List<RawTrack>? {
        try {
            val fileAsBytes = File(path).readBytes()
            val fileAsByteBuffer = fileAsByteBuffer(path)

            val formatHeader = fileAsByteBuffer.nextFourBytesAsString()
            if (formatHeader == null) {
                Timber.e("No header found.")
                return null
            }

            if (!isNsfFile(formatHeader)) {
                Timber.e("NSF header missing.")
                return null
            }

            val numberOfTracks = getNumberOfTracks(fileAsBytes)
            val gameTitle = getGameTitle(fileAsBytes)
            val gameArtist = getGameArtist(fileAsBytes)

            val tracks = mutableListOf<RawTrack>()

            for (index in 1..numberOfTracks) {
                tracks.add(
                    RawTrack(
                        path,
                        TAG_UNKNOWN,
                        gameArtist.orValidString(),
                        gameTitle,
                        LENGTH_UNKNOWN_MS,
                        -1,
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

    private fun isNsfFile(header: String) = header.contentEquals("NESM")
}
