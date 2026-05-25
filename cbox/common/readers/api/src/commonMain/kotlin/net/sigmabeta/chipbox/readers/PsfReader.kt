package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.utils.convert
import net.sigmabeta.chipbox.utils.convertUtf
import net.sigmabeta.sage.logging.Hatchet
import java.io.UnsupportedEncodingException

data class PsfTagInfo(
    val tags: Map<String, String>,
    val libReferences: List<String>, // ordered: _lib, _lib2, _lib3, ...
    val platform: Platform,
)

class PsfReader(private val hatchet: Hatchet) : Reader() {

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        readTagInfo(bytes)?.let { listOf(buildRawTrack(it.tags, identifier, it.platform)) }

    fun readTagInfo(bytes: ByteArray): PsfTagInfo? {
        val fileAsByteBuffer = bytesAsReader(bytes)
        val formatHeader = fileAsByteBuffer.nextBytesAsString(SIGNATURE_SIZE)

        if (formatHeader == null) {
            hatchet.w("PSF parse failed: file too small to contain header (${bytes.size} bytes).")
            return null
        }

        if (!isPsfFile(formatHeader)) {
            hatchet.w("PSF parse failed: header doesn't start with 'PSF' (got '$formatHeader', ${bytes.size} bytes).")
            return null
        }

        val platformCode = formatHeader.toByteArray(Charsets.US_ASCII)[SIGNATURE_INDEX_PLATFORM_CODE]
        val platform = platformForCode(platformCode)
        if (platform == null) {
            hatchet.w("PSF parse failed: unsupported platform code 0x%02X.".format(platformCode))
            return null
        }

        return try {
            val reservedAreaSize = fileAsByteBuffer.nextFourBytesAsInt()
            val programAreaSize = fileAsByteBuffer.nextFourBytesAsInt()

            val dataSize = reservedAreaSize + programAreaSize
            val tagsAreaSize = fileAsByteBuffer.size - dataSize - COMBINED_HEADER_SIZE

            val tagSectionStart = dataSize + FILE_HEADER_SIZE
            if (tagSectionStart > bytes.size) {
                hatchet.w(
                    "PSF parse failed: declared data section (reserved=$reservedAreaSize, " +
                        "program=$programAreaSize) extends past file end (${bytes.size} bytes)."
                )
                return null
            }

            if (tagsAreaSize <= 0) {
                hatchet.w(
                    "PSF parse failed: no tag section " +
                        "(file=${bytes.size}b, reserved=${reservedAreaSize}b, program=${programAreaSize}b)."
                )
                return null
            }

            fileAsByteBuffer.position(tagSectionStart)

            if (!isPsfTagValid(fileAsByteBuffer)) {
                hatchet.w("PSF parse failed: missing '[TAG]' marker at offset $tagSectionStart.")
                return null
            }

            val tagMap = HashMap<String, String>()
            readAllTags(tagsAreaSize, fileAsByteBuffer, tagMap)

            val libRefs = tagMap.keys
                .filter { key ->
                    key == PSF_TAG_KEY_LIB ||
                        (
                            key.startsWith(PSF_TAG_KEY_LIB) &&
                            key.removePrefix(PSF_TAG_KEY_LIB).all { c -> c.isDigit() }
                        )
                }
                .sortedBy { libKeyToIndex(it) }
                .mapNotNull { tagMap[it] }

            PsfTagInfo(tagMap, libRefs, platform)
        } catch (iae: IllegalArgumentException) {
            hatchet.w("PSF parse failed: illegal argument — ${iae.message}")
            null
        } catch (e: UnsupportedEncodingException) {
            hatchet.w("PSF parse failed: unsupported encoding — ${e.message}")
            null
        } catch (e: IndexOutOfBoundsException) {
            hatchet.w("PSF parse failed: buffer underflow (truncated file, ${bytes.size} bytes).")
            null
        }
    }

    fun buildRawTrack(
        tags: Map<String, String>,
        identifier: String,
        platform: Platform = Platform.OTHER,
    ) = RawTrack(
        identifier,
        "",
        tags[PSF_TAG_KEY_TITLE].orUnknown(),
        tags[PSF_TAG_KEY_ARTIST].orUnknown(),
        tags[PSF_TAG_KEY_GAME].orUnknown(),
        tags[PSF_TAG_KEY_LENGTH]?.toLengthMillis() ?: LENGTH_UNKNOWN_MS,
        -1,
        tags[PSF_TAG_KEY_FADE]?.toLengthMillis() ?: 0L,
        platform = platform,
    )

    private fun libKeyToIndex(key: String): Int = key.removePrefix("_lib").toIntOrNull() ?: 1

    // PSF "version" byte (header[3]) identifies the source system; unsupported codes return null.
    private fun platformForCode(platformCode: Byte): Platform? = when (platformCode) {
            0x01.toByte() -> Platform.PSX

            // PSF1  — Sony PlayStation
            0x02.toByte() -> Platform.PS2

            // PSF2  — Sony PlayStation 2
            0x11.toByte() -> Platform.SATURN

            // SSF   — Sega Saturn
            0x12.toByte() -> Platform.DREAMCAST

            // DSF   — Sega Dreamcast
            0x21.toByte() -> Platform.N64

            // USF   — Nintendo 64
            0x22.toByte() -> Platform.GAMEBOY_ADVANCE

            // GSF — Game Boy Advance
            0x24.toByte() -> Platform.NDS

            // 2SF   — Nintendo DS
            0x25.toByte() -> Platform.NDS

            // NCSF  — Nintendo DS (Nitro Composer)
            else -> null
        }

    private fun readAllTags(
        tagsAreaSize: Int,
        wrappedBuffer: ByteReader,
        tagMap: HashMap<String, String>
    ) {
        val tagData = ByteArray(tagsAreaSize)
        wrappedBuffer.get(tagData)

        var tags = tagData.convert().trim { it <= ' ' }

        if (tags.contains(PSF_UTF8_FLAG)) {
            tags = tagData.convertUtf().trim { it <= ' ' }
        }

        val lines = tags
            .split("\n".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()

        for (line in lines) {
            readSingleTag(line, tagMap)
        }
    }

    private fun readSingleTag(
        line: String,
        tagMap: HashMap<String, String>
    ) {
        if (!line.contains('=')) return
        tagMap[line.substringBefore('=')] = line.substringAfter('=')
    }

    private fun isPsfTagValid(wrappedBuffer: ByteReader): Boolean {
        val tagHeader = ByteArray(TAG_HEADER_SIZE)
        wrappedBuffer.get(tagHeader)

        return String(tagHeader) == PSF_TAG_HEADER
    }

    private fun isPsfFile(header: String) = header.startsWith("PSF")

    companion object {
        // "PSF" + 1-byte platform code makes up the 4-byte file signature.
        private const val SIGNATURE_SIZE = 4
        private const val SIGNATURE_INDEX_PLATFORM_CODE = 3

        private const val FILE_HEADER_SIZE = 16
        private const val TAG_HEADER_SIZE = 5
        private const val COMBINED_HEADER_SIZE = FILE_HEADER_SIZE + TAG_HEADER_SIZE

        private const val PSF_TAG_KEY_TITLE = "title"
        private const val PSF_TAG_KEY_GAME = "game"
        private const val PSF_TAG_KEY_ARTIST = "artist"
        private const val PSF_TAG_KEY_LENGTH = "length"
        private const val PSF_TAG_KEY_FADE = "fade"
        private const val PSF_TAG_KEY_LIB = "_lib"
        private const val PSF_TAG_HEADER = "[TAG]"
        private const val PSF_UTF8_FLAG = "utf8=1"
    }
}
