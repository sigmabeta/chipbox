package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import timber.log.Timber
import java.io.File
import java.io.UnsupportedEncodingException

object GbsReader : Reader() {
    override fun readTracksFromFile(path: String): List<RawTrack>? {
        try {
            val fileAsBytes = File(path).readBytes()
            val fileAsByteBuffer = fileAsByteBuffer(path)

            val formatHeader = fileAsByteBuffer.nextFourBytesAsString()
            if (formatHeader == null) {
                Timber.e("No header found.")
                return null
            }

            if (!isGbsFile(formatHeader)) {
                Timber.e("GBS header missing.")
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
        return fileAsBytes[0x04].toInt()
    }

    private fun getGameTitle(fileAsBytes: ByteArray): String {
        return try {
            fileAsBytes
                .decodeToString(0x10, 0x30, true)
                .trim()
        } catch (ex: Exception) {
            Timber.e("Unable to read game title: ${ex.message}")
            TAG_UNKNOWN
        }
    }

    private fun getGameArtist(fileAsBytes: ByteArray): String {
        return try {
            fileAsBytes
                .decodeToString(0x30, 0x50, true)
                .trim()
        } catch (ex: Exception) {
            Timber.e("Unable to read game title: ${ex.message}")
            TAG_UNKNOWN
        }
    }

    private fun isGbsFile(header: String) = header.startsWith("GBS")
}

/**
HEADER FIELDS

Offset Size Description
====== ==== ==========================
00     3  Identifier string ("GBS")
03     1  Version (1)
04     1  Number of songs (1-255)
05     1  First song (usually 1)
06     2  Load address ($400-$7fff)
08     2  Init address ($400-$7fff)
0a     2  Play address ($400-$7fff)
0c     2  Stack pointer
0e     1  Timer modulo  (see TIMING)
0f     1  Timer control (see TIMING)
10    32  Title string
30    32  Author string
50    32  Copyright string
70   nnnn Code and Data (see RST VECTORS)
 */
