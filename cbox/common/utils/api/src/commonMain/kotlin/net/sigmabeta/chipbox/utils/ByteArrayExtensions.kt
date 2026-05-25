package net.sigmabeta.chipbox.utils

/**
 * Decode as ISO-8859-1 (Latin-1): every byte maps 1:1 to a code point in U+0000..U+00FF. Done
 * by hand because the common stdlib has no Latin-1 decoder (only [decodeToString], which is UTF-8).
 */
fun ByteArray.convert(): String = buildString(size) {
    for (byte in this@convert) append((byte.toInt() and 0xFF).toChar())
}

/** Decode as UTF-8. */
fun ByteArray.convertUtf(): String = decodeToString()
