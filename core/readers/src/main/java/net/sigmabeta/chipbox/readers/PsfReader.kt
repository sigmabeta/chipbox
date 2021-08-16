package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.utils.convert
import net.sigmabeta.chipbox.utils.convertUtf
import java.io.File
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PsfReader : Reader() {
    private const val FILE_HEADER_SIZE = 16
    private const val TAG_HEADER_SIZE = 5
    private const val COMBINED_HEADER_SIZE = FILE_HEADER_SIZE + TAG_HEADER_SIZE

    private const val PSF_TAG_KEY_TITLE = "title"
    private const val PSF_TAG_KEY_GAME = "game"
    private const val PSF_TAG_KEY_PLATFORM = "platform"
    private const val PSF_TAG_KEY_ARTIST = "artist"
    private const val PSF_TAG_KEY_LENGTH = "length"

    private const val TAG_UNKNOWN_TITLE = "Unknown Track Title"
    private const val TAG_UNKNOWN_ARTIST = "Unknown Artist"
    private const val TAG_UNKNOWN_GAME = "Unknown Game"

    override fun readTracksFromFile(path: String): List<RawTrack>? {
        val tagMap: HashMap<String, String> = HashMap()

        val file = File(path)
        val fileAsBytes = file.readBytes()
        val fileSize = file.length().toInt()

        // Not sure why we check this.
        if (fileSize < 16) {
            return null
        }

        val header = ByteArray(4)

        val wrappedBuffer = ByteBuffer.wrap(fileAsBytes, 0, fileSize)
        wrappedBuffer.order(ByteOrder.LITTLE_ENDIAN)
        wrappedBuffer.get(header)

        if (!isPsfFile(header.take(3).toByteArray())) {
            return null
        }

        val platform = when (header[3]) {
            0x01.toByte() -> "Sony Playstation"
//            0x02.toByte() -> "Sony Playstation 2"
//            0x11.toByte() -> "Sega Saturn"
//            0x12.toByte() -> "Sega Dreamcast"
            else -> return null
        }

        tagMap[PSF_TAG_KEY_PLATFORM] = platform

        try {
            // Kotlin syntax kind of confusing. Gets the next Int sized chunk from buffer.
            val reservedAreaSize = wrappedBuffer.int
            val programAreaSize = wrappedBuffer.int

            val dataSize = reservedAreaSize + programAreaSize
            val tagsAreaSize = fileSize - dataSize - COMBINED_HEADER_SIZE

            // Move the reader to the start of the tag area
            wrappedBuffer.position(dataSize + FILE_HEADER_SIZE)

            if (wrappedBuffer.remaining() < 5) {
                return null
            }

            if (!isPsfTagValid(wrappedBuffer)) {
                return null
            }

            readAllTags(tagsAreaSize, wrappedBuffer, tagMap)
            return listOf(
                RawTrack(
                    path,
                    tagMap[PSF_TAG_KEY_TITLE] ?: TAG_UNKNOWN_TITLE,
                    tagMap[PSF_TAG_KEY_ARTIST] ?: TAG_UNKNOWN_ARTIST,
                    tagMap[PSF_TAG_KEY_GAME] ?: TAG_UNKNOWN_GAME,
                    getFileTrackLength(tagMap)
                )
            )
        } catch (iae: IllegalArgumentException) {
            return null
        } catch (e: UnsupportedEncodingException) {
            return null
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

    private fun getFileTrackLength(tagMap: HashMap<String, String>): Long {
        val lengthText = tagMap[PSF_TAG_KEY_LENGTH] ?: return 0
        val splitText = lengthText.split(":")

        val minutesText: String
        val secondsText: String

        when (splitText.size) {
            1 -> {
                minutesText = "0"
                secondsText = splitText[0]
            }
            2 -> {
                minutesText = splitText[0]
                secondsText = splitText[1]
            }
            else -> return 0L
        }

        val minutesInt = minutesText.toInt()
        val secondsInt = try {
            secondsText.toInt() + (minutesInt * 60)
        } catch (ex: NumberFormatException) {
            ((secondsText.toFloatOrNull() ?: 0.0f) + (minutesInt * 60)).toInt()
        }

        return secondsInt * 1000L
    }

    private fun isPsfTagValid(wrappedBuffer: ByteBuffer): Boolean {
        val tagHeader = ByteArray(5)
        wrappedBuffer.get(tagHeader)

        return String(tagHeader) == "[TAG]"
    }

    private fun isPsfFile(id: ByteArray) = id.contentEquals("PSF".toByteArray(Charsets.US_ASCII))

}