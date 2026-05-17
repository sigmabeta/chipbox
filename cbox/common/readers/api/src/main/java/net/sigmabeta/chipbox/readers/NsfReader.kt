package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.FADE_LENGTH_MS
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet
import java.io.UnsupportedEncodingException

class NsfReader(private val hatchet: Hatchet) : Reader() {
    @OptIn(ExperimentalStdlibApi::class)
    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? {
        try {
            val fileAsByteBuffer = bytesAsByteBuffer(bytes)

            val formatHeader = fileAsByteBuffer.nextBytesAsString(HEADER_MAGIC_SIZE)
            if (formatHeader == null) {
                hatchet.w("NSF parse failed: file too small to contain header (${bytes.size} bytes).")
                return null
            }

            if (!isNsfFile(formatHeader)) {
                hatchet.w("NSF parse failed: header doesn't start with 'NESM' (got '$formatHeader').")
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
                        platform = Platform.NES,
                    )
                )
            }
            return tracks
        } catch (iae: IllegalArgumentException) {
            hatchet.w("NSF parse failed: illegal argument — ${iae.message}")
            return null
        } catch (e: UnsupportedEncodingException) {
            hatchet.w("NSF parse failed: unsupported encoding — ${e.message}")
            return null
        }
    }

    private fun getNumberOfTracks(fileAsBytes: ByteArray): Int = fileAsBytes[OFFSET_TOTAL_SONGS].toInt() and BYTE_MASK

    private fun getGameTitle(fileAsBytes: ByteArray): String = try {
            fileAsBytes
                .decodeToString(OFFSET_GAME_TITLE, OFFSET_GAME_ARTIST, true)
                .substringBefore(0.toChar())
                .trim()
        } catch (ex: Exception) {
            hatchet.w("NSF: unable to read game title — ${ex.message}")
            TAG_UNKNOWN
        }

    private fun getGameArtist(fileAsBytes: ByteArray): String = try {
            fileAsBytes
                .decodeToString(OFFSET_GAME_ARTIST, OFFSET_COPYRIGHT, true)
                .substringBefore(0.toChar())
                .trim()
        } catch (ex: Exception) {
            hatchet.w("NSF: unable to read artist — ${ex.message}")
            TAG_UNKNOWN
        }

    private fun isNsfFile(header: String) = header.contentEquals(HEADER_MAGIC)

    companion object {
        private const val HEADER_MAGIC = "NESM"
        private const val HEADER_MAGIC_SIZE = 4

        // NSF header layout (offsets into the file).
        private const val OFFSET_TOTAL_SONGS = 0x06
        private const val OFFSET_GAME_TITLE = 0x0E
        private const val OFFSET_GAME_ARTIST = 0x2E
        private const val OFFSET_COPYRIGHT = 0x4E

        private const val BYTE_MASK = 0xFF
    }
}
