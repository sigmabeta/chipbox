package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

class SpcReader(private val hatchet: Hatchet) : Reader() {
    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? {
        val tags = readTags(bytes, identifier) ?: return null
        return listOf(
            RawTrack(
                identifier,
                "",
                tags.songTitle,
                tags.artistName,
                tags.gameTitle,
                tags.trackLengthMs,
                0,
                tags.fadeLengthMs,
                platform = Platform.SNES,
            )
        )
    }

    /**
     * Parse an SPC file's ID666 / xid6 metadata, or null when [bytes] aren't a valid SPC or carry
     * no metadata. Shared with [RsnReader], which calls this once per SPC member of an RSN archive.
     * Never throws — a malformed body (e.g. a truncated member) is reported as null.
     */
    internal fun readTags(bytes: ByteArray, identifier: String): SpcTags? {
        try {
            val fileAsByteBuffer = bytesAsReader(bytes)
            val formatHeader = fileAsByteBuffer.nextBytesAsString(HEADER_MAGIC_SIZE)
            if (formatHeader == null) {
                hatchet.w("SPC parse failed: file too small to contain header (${bytes.size} bytes).")
                return null
            }

            if (!isSpcFile(formatHeader)) {
                hatchet.w("SPC parse failed: header missing (got '$formatHeader').")
                return null
            }

            val spcMainTag = readMainTag(fileAsByteBuffer) ?: return null

            // xid6 holds the un-truncated game/song/artist names; the 32-byte ID666 fields
            // chop anything longer (e.g. "Teenage Mutant Ninja Turtles: To" → full name lives
            // in xid6 only). Prefer xid6 values when present.
            val extendedTag = readExtendedTag(bytes)

            return SpcTags(
                songTitle = extendedTag?.songTitle ?: spcMainTag.songTitle,
                gameTitle = extendedTag?.gameTitle ?: spcMainTag.gameTitle,
                artistName = extendedTag?.artistName ?: spcMainTag.artistName,
                trackLengthMs = spcMainTag.trackLengthMs,
                fadeLengthMs = spcMainTag.fadeLengthMs.coerceAtLeast(0L),
            )
        } catch (iae: IllegalArgumentException) {
            hatchet.w("SPC parse failed: illegal argument — ${iae.message}")
            return null
        } catch (e: Exception) {
            hatchet.w("SPC parse failed for $identifier: ${e.message}")
            return null
        }
    }

    private fun readMainTag(fileAsByteBuffer: ByteReader): SpcMainTag? {
        val hasHeaderInfo = fileAsByteBuffer
            .nextBytes(LENGTH_HEADER_INFO_FIELD)
            ?.last()
            ?.equals(0x1A.toByte()) ?: false

        if (!hasHeaderInfo) {
            hatchet.w("SPC: file has no metadata.")
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
            hatchet.i("SPC Minor Ver: $minorVersion")
            hatchet.i("SPC Registers: $spcRegistersIgnored")
            hatchet.i("Dumper: $dumperName")
            hatchet.i("Dump Date: $comments")
            hatchet.i("Comments: $dumpDate")
        }

        // SPC lengths are stored as string-encoded numbers?!?!? Apparently this is supposed to not
        // always be the case, but I've never seen an example of it not being the case, so until then,
        // this is assumed to be how it works.
        val lengthMs = lengthSecondsString?.toLongOrNull()?.times(MILLIS_PER_SECOND) ?: LENGTH_UNKNOWN_MS
        val fadeLengthMs = fadeLengthMillisString?.toLongOrNull() ?: LENGTH_UNKNOWN_MS

        return SpcMainTag(
            songTitle.orUnknown(),
            gameTitle.orUnknown(),
            lengthMs,
            fadeLengthMs,
            artistName.orUnknown()
        )
    }

    private fun isSpcFile(header: String) = header.contentEquals(HEADER_MAGIC)

    /**
     * Parse the extended ID666 (xid6) chunk that may follow the standard 0x10200-byte SPC body.
     * Only the three string fields whose ID666 counterparts are 32-byte-capped are extracted;
     * everything else is skipped. Returns null when the chunk is absent or malformed.
     */
    private fun readExtendedTag(bytes: ByteArray): SpcExtendedTag? {
        if (bytes.size < XID6_OFFSET + XID6_HEADER_SIZE) return null
        val buf = bytesAsReader(bytes)
        buf.position(XID6_OFFSET)
        val magic = buf.nextBytes(XID6_MAGIC_SIZE) ?: return null
        if (!magic.decodeToString().contentEquals(XID6_MAGIC)) return null
        val chunkSize = buf.nextFourBytesAsInt()
        if (chunkSize <= 0) return null
        val end = (buf.position() + chunkSize).coerceAtMost(bytes.size)

        var songTitle: String? = null
        var gameTitle: String? = null
        var artistName: String? = null

        while (buf.position() + XID6_SUBCHUNK_HEADER_SIZE <= end) {
            val id = buf.get().toInt() and BYTE_MASK
            val type = buf.get().toInt() and BYTE_MASK
            val data = buf.readShortLe().toInt() and SHORT_MASK

            when (type) {
                XID6_TYPE_INLINE -> Unit

                // 16-bit value lives in `data`; no payload follows.
                XID6_TYPE_STRING, XID6_TYPE_INTEGER -> {
                    if (data == 0) continue
                    if (buf.position() + data > end) break
                    val payload = buf.nextBytes(data) ?: break
                    val pad = (XID6_ALIGNMENT - (data % XID6_ALIGNMENT)) % XID6_ALIGNMENT
                    if (pad > 0 && buf.position() + pad <= end) {
                        buf.nextBytes(pad)
                    }
                    if (type == XID6_TYPE_STRING) {
                        val str = payload.decodeToString()
                            .substringBefore(0.toChar())
                            .trim()
                        if (str.isNotEmpty()) {
                            when (id) {
                                XID6_ID_SONG -> songTitle = str
                                XID6_ID_GAME -> gameTitle = str
                                XID6_ID_ARTIST -> artistName = str
                            }
                        }
                    }
                }

                else -> break // Unknown type — bail to avoid misaligning subsequent reads.
            }
        }

        if (songTitle == null && gameTitle == null && artistName == null) return null
        return SpcExtendedTag(songTitle, gameTitle, artistName)
    }

    companion object {
        private const val HEADER_MAGIC = "SNES-SPC700 Sound File Data v0.30"

        // 33-byte magic string (32 chars + the trailing v-version digit) is read up front.
        private const val HEADER_MAGIC_SIZE = 33

        // 3-byte field after the magic; its last byte is 0x1A when ID666 metadata is present.
        private const val LENGTH_HEADER_INFO_FIELD = 3
        private const val MILLIS_PER_SECOND = 1_000L
        private const val BYTE_MASK = 0xFF
        private const val SHORT_MASK = 0xFFFF
        private const val SHOULD_LOG_EXTRA_INFO = false

        private const val LENGTH_SPC_REGISTERS = 9
        private const val LENGTH_TAG_STANDARD = 32
        private const val LENGTH_TAG_DUMPER_NAME = 16
        private const val LENGTH_TAG_DUMP_DATE = 11
        private const val LENGTH_TAG_TRACK_LENGTH = 3
        private const val LENGTH_TAG_FADE_LENGTH = 5

        private const val XID6_OFFSET = 0x1_0200
        private const val XID6_MAGIC = "xid6"
        private const val XID6_MAGIC_SIZE = 4
        private const val XID6_HEADER_SIZE = 8
        private const val XID6_SUBCHUNK_HEADER_SIZE = 4
        private const val XID6_ALIGNMENT = 4

        private const val XID6_TYPE_INLINE = 0
        private const val XID6_TYPE_STRING = 1
        private const val XID6_TYPE_INTEGER = 4

        private const val XID6_ID_SONG = 0x01
        private const val XID6_ID_GAME = 0x02
        private const val XID6_ID_ARTIST = 0x03
    }
}

/** Resolved SPC metadata, with xid6 values already preferred over the truncated ID666 fields. */
data class SpcTags(
    val songTitle: String,
    val gameTitle: String,
    val artistName: String,
    val trackLengthMs: Long,
    val fadeLengthMs: Long,
)

data class SpcMainTag(
    val songTitle: String,
    val gameTitle: String,
    val trackLengthMs: Long,
    val fadeLengthMs: Long,
    val artistName: String
)

data class SpcExtendedTag(
    val songTitle: String?,
    val gameTitle: String?,
    val artistName: String?,
)
