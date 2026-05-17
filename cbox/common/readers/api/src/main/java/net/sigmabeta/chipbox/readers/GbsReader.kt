package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.FADE_LENGTH_MS
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet
import java.io.UnsupportedEncodingException

class GbsReader(private val hatchet: Hatchet) : Reader() {
    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? {
        try {
            val fileAsByteBuffer = bytesAsByteBuffer(bytes)

            val formatHeader = fileAsByteBuffer.nextBytesAsString(HEADER_READ_SIZE)
            if (formatHeader == null) {
                hatchet.w("GBS parse failed: file too small to contain header (${bytes.size} bytes).")
                return null
            }

            if (!isGbsFile(formatHeader)) {
                hatchet.w("GBS parse failed: header doesn't start with 'GBS' (got '$formatHeader').")
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
                        FADE_LENGTH_MS,
                        platform = Platform.GAMEBOY,
                    )
                )
            }
            return tracks
        } catch (iae: IllegalArgumentException) {
            hatchet.w("GBS parse failed: illegal argument — ${iae.message}")
            return null
        } catch (e: UnsupportedEncodingException) {
            hatchet.w("GBS parse failed: unsupported encoding — ${e.message}")
            return null
        }
    }

    private fun getNumberOfTracks(fileAsBytes: ByteArray): Int =
        fileAsBytes[OFFSET_NUMBER_OF_SONGS].toInt() and BYTE_MASK

    private fun getGameTitle(fileAsBytes: ByteArray): String = try {
            fileAsBytes
                .decodeToString(OFFSET_TITLE, OFFSET_AUTHOR, true)
                .substringBefore(0.toChar())
                .trim()
        } catch (ex: Exception) {
            hatchet.w("GBS: unable to read game title — ${ex.message}")
            TAG_UNKNOWN
        }

    private fun getGameArtist(fileAsBytes: ByteArray): String = try {
            fileAsBytes
                .decodeToString(OFFSET_AUTHOR, OFFSET_COPYRIGHT, true)
                .substringBefore(0.toChar())
                .trim()
        } catch (ex: Exception) {
            hatchet.w("GBS: unable to read artist — ${ex.message}")
            TAG_UNKNOWN
        }

    private fun isGbsFile(header: String) = header.startsWith(HEADER_MAGIC)

    companion object {
        private const val HEADER_MAGIC = "GBS"
        private const val HEADER_READ_SIZE = 4

        // GBS header layout (offsets into the file).
        private const val OFFSET_NUMBER_OF_SONGS = 0x04
        private const val OFFSET_TITLE = 0x10
        private const val OFFSET_AUTHOR = 0x30
        private const val OFFSET_COPYRIGHT = 0x50

        private const val BYTE_MASK = 0xFF
    }
}

/*
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
