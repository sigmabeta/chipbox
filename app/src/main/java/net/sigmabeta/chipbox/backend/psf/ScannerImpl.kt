package net.sigmabeta.chipbox.backend.psf

import net.sigmabeta.chipbox.backend.Scanner
import net.sigmabeta.chipbox.util.convert
import net.sigmabeta.chipbox.util.convertUtf
import timber.log.Timber
import java.io.File
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.ByteOrder


class ScannerImpl : Scanner {
    override fun getBackendId() = BackendImpl.ID

    private var tagMap: HashMap<String, String>? = null

    override fun fileInfoSetup(path: String): String? {
        tagMap = HashMap()

        val file = File(path)
        val fileAsBytes = file.readBytes()
        val fileSize = file.length().toInt()

        // Not sure why we check this.
        if (fileSize < 16)
            return "File too small."

        val header = ByteArray(4)

        val wrappedBuffer = ByteBuffer.wrap(fileAsBytes, 0, fileSize)
        wrappedBuffer.order(ByteOrder.LITTLE_ENDIAN)
        wrappedBuffer.get(header)

        if (isPsfFile(header)) {
            val platform = when (header[3]) {
                0x01.toByte() -> "Sony Playstation"
                0x02.toByte() -> "Sony Playstation 2"
                0x11.toByte() -> "Sega Saturn"
                0x12.toByte() -> "Sega Dreamcast"
                else -> return "Invalid file header."
            }

            tagMap?.set("platform", platform)

            try {
                val firstLength = wrappedBuffer.getInt()
                val secondLength = wrappedBuffer.getInt()

                wrappedBuffer.position(firstLength + secondLength + 16)

                if (wrappedBuffer.remaining() >= 5) {
                    val tagHeader = ByteArray(5)
                    wrappedBuffer.get(tagHeader)

                    if (String(tagHeader) == "[TAG]") {
                        val tagData = ByteArray(fileSize - secondLength - firstLength - 21)
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
                            Timber.d("TAG: %s", line)

                            val parts = line
                                    .split("=".toRegex())
                                    .dropLastWhile { it.isEmpty() }
                                    .toTypedArray()

                            if (parts.size >= 2) {
                                tagMap?.set(parts[0], parts[1])
                            }
                        }
                        return null
                    }
                }
            } catch (iae: IllegalArgumentException) {
                Timber.e(iae.message)
                return iae.message
            } catch (e: UnsupportedEncodingException) {
                Timber.e(e.message)
                return e.message
            }
        }
        return "PSF header missing"
    }

    override fun fileInfoGetTrackCount() = 1

    override fun fileInfoSetTrackNumber(trackNumber: Int) = Unit

    override fun fileInfoTeardown() {
        tagMap = null
    }

    override fun getFileTrackLength(): Long {
        //int decimal = 0;
        val lengthText = tagMap?.get("length") ?: return 0
        val splitText = lengthText.split(":")

        val minutesText: String
        val secondsText: String

        if (splitText.size == 1) {
            minutesText = "0"
            secondsText = splitText[0]
        } else if (splitText.size == 2)  {
            minutesText = splitText[0]
            secondsText = splitText[1]
        } else {
            Timber.e("Invalid length string: %s", lengthText)
            return 0L
        }

        val minutesInt = minutesText.toInt()
        val secondsInt = try {
            secondsText.toInt() + (minutesInt * 60)
        } catch (ex: NumberFormatException) {
            Timber.e("Invalid integer: %s", secondsText)
            ((secondsText.toFloatOrNull() ?: 0.0f) + (minutesInt * 60)).toInt()
        }

        return secondsInt * 1000L
    }

    override fun getFileIntroLength() = 0L

    override fun getFileLoopLength() = 0L

    override fun getFileTitle() = tagMap?.get("title")?.toByteArray()

    override fun getFileGameTitle() = tagMap?.get("game")?.toByteArray()

    override fun getFilePlatform() = tagMap?.get("platform")?.toByteArray()

    override fun getFileArtist() = tagMap?.get("artist")?.toByteArray()

    private fun isPsfFile(id: ByteArray) =
            id[0] == 'P'.toByte() && id[1] == 'S'.toByte() && id[2] == 'F'.toByte()
}