package net.sigmabeta.chipbox.readers

internal fun ByteReader.nextFourBytesAsInt() = readIntLe()

internal fun ByteReader.nextBytes(numberOfBytes: Int): ByteArray? = readBytes(numberOfBytes)

internal fun ByteReader.nextBytesAsString(numberOfBytes: Int) = nextBytes(numberOfBytes)
    ?.decodeToString()
    ?.substringBefore(0.toChar())
    ?.trim()

internal fun ByteReader.nextBytesAsInt(numberOfBytes: Int): Int {
    val valueBytes = nextBytes(numberOfBytes) ?: return 0
    return signedBigEndianInt(valueBytes)
}

/**
 * Interpret [bytes] as a big-endian two's-complement integer — matches the old
 * `BigInteger(ByteArray).toInt()`: sign-extend from the most significant byte, then fold in
 * each byte. Only the low 32 bits are kept (as `toInt()` did).
 */
private fun signedBigEndianInt(bytes: ByteArray): Int {
    if (bytes.isEmpty()) return 0
    var result = if (bytes[0] < 0) -1 else 0
    for (b in bytes) result = (result shl 8) or (b.toInt() and 0xFF)
    return result
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
    val number = match?.groupValues?.get(1)?.toIntOrNull()
    val rest = match?.groupValues?.get(2)?.trim()
    return if (number != null && !rest.isNullOrEmpty()) {
        FilenameMeta(number, rest)
    } else {
        FilenameMeta(null, base)
    }
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

internal fun bytesAsReader(bytes: ByteArray): ByteReader = ByteReader.wrap(bytes)

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

    // Parse both components leniently: a blank or non-numeric minutes/seconds field — e.g. a
    // malformed ":30", "1:", or "" tag value — contributes 0 rather than throwing. A bad length/fade
    // tag must never abort the scan (and via PSF _lib chain-tag merging, one bad .psflib would
    // otherwise take down every .minipsf that includes it).
    val minutesInt = minutesText.toIntOrNull() ?: 0
    val secondsInt = (secondsText.toFloatOrNull() ?: 0.0f).toInt() + (minutesInt * SECONDS_PER_MINUTE)

    return secondsInt * MILLIS_PER_SECOND
}

const val LENGTH_UNKNOWN_MS = -1L
const val TAG_UNKNOWN = "Unknown"
private const val TAG_PSF_PLACEHOLDER = "<?>"

// "H:M:S"-formatted time string has three colon-separated parts.
private const val TIME_PARTS_HMS = 3
private const val SECONDS_PER_MINUTE = 60
private const val MILLIS_PER_SECOND = 1000L
