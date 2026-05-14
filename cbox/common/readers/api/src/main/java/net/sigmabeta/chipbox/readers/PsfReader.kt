package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.utils.convert
import net.sigmabeta.chipbox.utils.convertUtf
import net.sigmabeta.sage.logging.Hatchet
import java.io.UnsupportedEncodingException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

data class PsfTagInfo(
    val tags: Map<String, String>,
    val libReferences: List<String>,  // ordered: _lib, _lib2, _lib3, ...
)

class PsfReader(private val hatchet: Hatchet) : Reader() {

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        readTagInfo(bytes)?.let { listOf(buildRawTrack(it.tags, identifier)) }

    fun readTagInfo(bytes: ByteArray): PsfTagInfo? {
        val fileAsByteBuffer = bytesAsByteBuffer(bytes)
        val formatHeader = fileAsByteBuffer.nextBytesAsString(4)

        if (formatHeader == null) {
            hatchet.w("PSF parse failed: file too small to contain header (${bytes.size} bytes).")
            return null
        }

        if (!isPsfFile(formatHeader)) {
            hatchet.w("PSF parse failed: header doesn't start with 'PSF' (got '$formatHeader', ${bytes.size} bytes).")
            return null
        }

        val platformCode = formatHeader.toByteArray(Charsets.US_ASCII)[3]
        if (!isSupportedPlatform(platformCode)) {
            hatchet.w("PSF parse failed: unsupported platform code 0x%02X.".format(platformCode))
            return null
        }

        return try {
            val reservedAreaSize = fileAsByteBuffer.nextFourBytesAsInt()
            val programAreaSize = fileAsByteBuffer.nextFourBytesAsInt()

            val dataSize = reservedAreaSize + programAreaSize
            val tagsAreaSize = fileAsByteBuffer.array().size - dataSize - COMBINED_HEADER_SIZE

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
                .filter { it == PSF_TAG_KEY_LIB || (it.startsWith("_lib") && it.removePrefix("_lib").all { c -> c.isDigit() }) }
                .sortedBy { libKeyToIndex(it) }
                .mapNotNull { tagMap[it] }

            PsfTagInfo(tagMap, libRefs)
        } catch (iae: IllegalArgumentException) {
            hatchet.w("PSF parse failed: illegal argument — ${iae.message}")
            null
        } catch (e: UnsupportedEncodingException) {
            hatchet.w("PSF parse failed: unsupported encoding — ${e.message}")
            null
        } catch (e: BufferUnderflowException) {
            hatchet.w("PSF parse failed: buffer underflow (truncated file, ${bytes.size} bytes).")
            null
        }
    }

    fun buildRawTrack(tags: Map<String, String>, identifier: String) = RawTrack(
        identifier,
        "",
        tags[PSF_TAG_KEY_TITLE].orUnknown(),
        tags[PSF_TAG_KEY_ARTIST].orUnknown(),
        tags[PSF_TAG_KEY_GAME].orUnknown(),
        tags[PSF_TAG_KEY_LENGTH]?.toLengthMillis() ?: LENGTH_UNKNOWN_MS,
        -1,
        tags[PSF_TAG_KEY_FADE]?.toLengthMillis() ?: 0L,
    )

    private fun libKeyToIndex(key: String): Int = key.removePrefix("_lib").toIntOrNull() ?: 1

    private fun isSupportedPlatform(platformCode: Byte): Boolean {
        return when (platformCode) {
            0x01.toByte() -> true // "Sony Playstation"
            0x02.toByte() -> true // "Sony Playstation 2"
            0x11.toByte() -> true // "Sega Saturn"
            0x12.toByte() -> true // "Sega Dreamcast"
            0x22.toByte() -> true // "GBA"
            0x24.toByte() -> true // "Nintendo DS"
            else -> false
        }
    }

    private fun readAllTags(
        tagsAreaSize: Int,
        wrappedBuffer: ByteBuffer,
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

    private fun isPsfTagValid(wrappedBuffer: ByteBuffer): Boolean {
        val tagHeader = ByteArray(5)
        wrappedBuffer.get(tagHeader)

        return String(tagHeader) == PSF_TAG_HEADER
    }

    private fun isPsfFile(header: String) = header.startsWith("PSF")

    companion object {
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
