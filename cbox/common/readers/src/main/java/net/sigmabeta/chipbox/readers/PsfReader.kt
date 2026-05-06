package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.utils.convert
import net.sigmabeta.chipbox.utils.convertUtf
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.logging.Hatchet
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer

private val hatchet: Hatchet = BluntHatchet()

data class PsfTagInfo(
    val tags: Map<String, String>,
    val libReferences: List<String>,  // ordered: _lib, _lib2, _lib3, ...
)

object PsfReader : Reader() {
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

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        readTagInfo(bytes)?.let { listOf(buildRawTrack(it.tags, identifier)) }

    fun readTagInfo(bytes: ByteArray): PsfTagInfo? {
        val fileAsByteBuffer = bytesAsByteBuffer(bytes)
        val formatHeader = fileAsByteBuffer.nextBytesAsString(4)

        if (formatHeader == null) {
            hatchet.e("No header found.")
            return null
        }

        if (!isPsfFile(formatHeader)) {
            hatchet.e("PSF header missing.")
            return null
        }

        if (!isSupportedPlatform(formatHeader.toByteArray(Charsets.US_ASCII)[3])) {
            hatchet.e("Unsupported platform.")
            return null
        }

        return try {
            val reservedAreaSize = fileAsByteBuffer.nextFourBytesAsInt()
            val programAreaSize = fileAsByteBuffer.nextFourBytesAsInt()

            val dataSize = reservedAreaSize + programAreaSize
            val tagsAreaSize = fileAsByteBuffer.array().size - dataSize - COMBINED_HEADER_SIZE

            fileAsByteBuffer.position(dataSize + FILE_HEADER_SIZE)

            if (!isPsfTagValid(fileAsByteBuffer)) {
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
            hatchet.e("Illegal argument: ${iae.message}")
            null
        } catch (e: UnsupportedEncodingException) {
            hatchet.e("Unsupported Encoding: ${e.message}")
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
        tags[PSF_TAG_KEY_FADE]?.toLengthMillis() ?: 0 > 0
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
        val parts = line
            .split("=".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()

        if (parts.size >= 2) {
            val tagKey = parts[0]
            val tagValue = parts[1]

            tagMap[tagKey] = tagValue
        }
    }

    private fun isPsfTagValid(wrappedBuffer: ByteBuffer): Boolean {
        val tagHeader = ByteArray(5)
        wrappedBuffer.get(tagHeader)

        return String(tagHeader) == PSF_TAG_HEADER
    }

    private fun isPsfFile(header: String) = header.startsWith("PSF")

}