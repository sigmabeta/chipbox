package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.utils.convert
import net.sigmabeta.chipbox.utils.convertUtf
import timber.log.Timber
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer

object PsfReader : Reader() {
    private const val FILE_HEADER_SIZE = 16
    private const val TAG_HEADER_SIZE = 5
    private const val COMBINED_HEADER_SIZE = FILE_HEADER_SIZE + TAG_HEADER_SIZE

    private const val PSF_TAG_KEY_TITLE = "title"
    private const val PSF_TAG_KEY_GAME = "game"
    private const val PSF_TAG_KEY_ARTIST = "artist"
    private const val PSF_TAG_KEY_LENGTH = "length"
    private const val PSF_TAG_KEY_FADE = "fade"

    override fun readTracksFromFile(path: String): List<RawTrack>? {
        val fileAsByteBuffer = fileAsByteBuffer(path)
        val formatHeader = fileAsByteBuffer.nextBytesAsString(4)

        if (formatHeader == null) {
            Timber.e("No header found.")
            return null
        }

        if (!isPsfFile(formatHeader)) {
            Timber.e("PSF header missing.")
            return null
        }

        if (!isSupportedPlatform(formatHeader.toByteArray(Charsets.US_ASCII)[3])) {
            Timber.e("Unsupported platform.")
            return null
        }

        try {
            val reservedAreaSize = fileAsByteBuffer.nextFourBytesAsInt()
            val programAreaSize = fileAsByteBuffer.nextFourBytesAsInt()

            val dataSize = reservedAreaSize + programAreaSize
            val tagsAreaSize = fileAsByteBuffer.array().size - dataSize - COMBINED_HEADER_SIZE

            // Move the reader to the start of the tag area
            fileAsByteBuffer.position(dataSize + FILE_HEADER_SIZE)

            if (!isPsfTagValid(fileAsByteBuffer)) {
                return null
            }

            val tagMap: HashMap<String, String> = HashMap()

            readAllTags(tagsAreaSize, fileAsByteBuffer, tagMap)
            return listOf(
                RawTrack(
                    path,
                    tagMap[PSF_TAG_KEY_TITLE].orUnknown(),
                    tagMap[PSF_TAG_KEY_ARTIST].orUnknown(),
                    tagMap[PSF_TAG_KEY_GAME].orUnknown(),
                    tagMap[PSF_TAG_KEY_LENGTH]?.toLengthMillis() ?: LENGTH_UNKNOWN_MS,
                    -1,
                    tagMap[PSF_TAG_KEY_FADE]?.toLengthMillis() ?: 0 > 0
                )
            )
        } catch (iae: IllegalArgumentException) {
            Timber.e("Illegal argument: ${iae.message}")
            return null
        } catch (e: UnsupportedEncodingException) {
            Timber.e("Unsupported Encoding: ${e.message}")
            return null
        }
    }

    private fun isSupportedPlatform(platformCode: Byte): Boolean {
        return when (platformCode) {
            0x01.toByte() -> true // "Sony Playstation"
            0x02.toByte() -> true // "Sony Playstation 2"
            0x22.toByte() -> true // "GBA"
            //            0x11.toByte() -> Unit // "Sega Saturn"
            //            0x12.toByte() -> Unit // "Sega Dreamcast"
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

        if (tags.contains("utf8=1")) {
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

        return String(tagHeader) == "[TAG]"
    }

    private fun isPsfFile(header: String) = header.startsWith("PSF")

}