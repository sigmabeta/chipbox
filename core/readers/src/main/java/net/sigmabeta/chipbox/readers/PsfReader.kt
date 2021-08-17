package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.utils.convert
import net.sigmabeta.chipbox.utils.convertUtf
import timber.log.Timber
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
    private const val PSF_TAG_KEY_ARTIST = "artist"
    private const val PSF_TAG_KEY_LENGTH = "length"
    private const val PSF_TAG_KEY_FADE = "fade"

    private const val TAG_UNKNOWN = "Unknown"

    override fun readTracksFromFile(path: String): List<RawTrack>? {
        val tagMap: HashMap<String, String> = HashMap()

        val file = File(path)
        val fileAsBytes = file.readBytes()
        val fileSize = file.length().toInt()

        val fileAsByteBuffer = ByteBuffer.wrap(fileAsBytes, 0, fileSize)
        fileAsByteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val formatHeader = fileAsByteBuffer.nextFourBytesAsString()
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
            val tagsAreaSize = fileSize - dataSize - COMBINED_HEADER_SIZE

            // Move the reader to the start of the tag area
            fileAsByteBuffer.position(dataSize + FILE_HEADER_SIZE)

            if (!isPsfTagValid(fileAsByteBuffer)) {
                return null
            }

            readAllTags(tagsAreaSize, fileAsByteBuffer, tagMap)
            return listOf(
                RawTrack(
                    path,
                    tagMap[PSF_TAG_KEY_TITLE] ?: TAG_UNKNOWN,
                    tagMap[PSF_TAG_KEY_ARTIST] ?: TAG_UNKNOWN,
                    tagMap[PSF_TAG_KEY_GAME] ?: TAG_UNKNOWN,
                    tagMap[PSF_TAG_KEY_LENGTH]?.toLengthMillis() ?: 150_000L,
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
            //            0x02.toByte() -> Unit // "Sony Playstation 2"
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

    private fun String.toLengthMillis(): Long {
        val splitText = split(":")

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

    private fun isPsfFile(header: String) = header.startsWith("PSF")

}