package net.sigmabeta.chipbox.readers

import java.math.BigInteger
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun ByteBuffer.nextFourBytesAsInt() = int

internal fun ByteBuffer.nextBytes(numberOfBytes: Int): ByteArray? {
    val headerArray = ByteArray(numberOfBytes)

    try {
        get(headerArray)
    } catch (ex: BufferUnderflowException) {
        return null
    }

    return headerArray
}

internal fun ByteBuffer.nextBytesAsString(numberOfBytes: Int) = nextBytes(numberOfBytes)
    ?.toString(Charsets.UTF_8)
    ?.substringBefore(0.toChar())
    ?.trim()

internal fun ByteBuffer.nextBytesAsInt(numberOfBytes: Int): Int {
    val lengthSecondsBytes = nextBytes(numberOfBytes)

    val bigInteger = BigInteger(lengthSecondsBytes)
    return bigInteger.toInt()
}

data class FilenameMeta(
    val trackNumber: Int?,
    val title: String,
)

/**
 * Best-effort title and track number from a filename, for files that carry no embedded metadata —
 * common for NCSF/PSF rips where the title lives only in the filename, e.g. "01 Prologue.minincsf".
 * A leading run of digits followed by a separator (space, '-', '.', etc.) is taken as the track
 * number and stripped; the remainder is the title. With no such prefix the whole base name is the
 * title and the track number is null. The extension is dropped first.
 */
fun deriveMetaFromFilename(filename: String): FilenameMeta {
    val base = filename.substringBeforeLast('.', filename).trim()
    if (base.isEmpty()) return FilenameMeta(null, filename)

    val match = FILENAME_TRACK_PREFIX.find(base)
    if (match != null) {
        val number = match.groupValues[1].toIntOrNull()
        val rest = match.groupValues[2].trim()
        if (number != null && rest.isNotEmpty()) {
            return FilenameMeta(number, rest)
        }
    }
    return FilenameMeta(null, base)
}

// Leading 1-4 digit track number, then one or more separators, then the title. Requiring a
// separator avoids mis-splitting names like "1up" into 1 + "up".
private val FILENAME_TRACK_PREFIX = Regex("""^(\d{1,4})[\s\-._)\]]+(.+)$""")

fun String?.orUnknown(): String {
    if (this == null) {
        return TAG_UNKNOWN
    }

    if (this == TAG_PSF_PLACEHOLDER) {
        return TAG_UNKNOWN
    }

    if (this.isEmpty()) {
        return TAG_UNKNOWN
    }

    return this
}

internal fun bytesAsByteBuffer(bytes: ByteArray): ByteBuffer =
    ByteBuffer.wrap(bytes, 0, bytes.size).order(ByteOrder.LITTLE_ENDIAN)

internal fun String.toLengthMillis(): Long {
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

        TIME_PARTS_HMS -> {
            minutesText = splitText[1]
            secondsText = splitText[2]
        }

        else -> return 0L
    }

    val minutesInt = minutesText.toInt()
    val secondsInt = try {
        secondsText.toInt() + (minutesInt * SECONDS_PER_MINUTE)
    } catch (ex: NumberFormatException) {
        ((secondsText.toFloatOrNull() ?: 0.0f) + (minutesInt * SECONDS_PER_MINUTE)).toInt()
    }

    return secondsInt * MILLIS_PER_SECOND
}

const val LENGTH_UNKNOWN_MS = -1L
const val TAG_UNKNOWN = "Unknown"
private const val TAG_PSF_PLACEHOLDER = "<?>"

// "H:M:S"-formatted time string has three colon-separated parts.
private const val TIME_PARTS_HMS = 3
private const val SECONDS_PER_MINUTE = 60
private const val MILLIS_PER_SECOND = 1000L
