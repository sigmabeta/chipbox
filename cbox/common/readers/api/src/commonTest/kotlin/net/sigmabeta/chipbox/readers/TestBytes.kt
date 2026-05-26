package net.sigmabeta.chipbox.readers

/**
 * Pure-Kotlin byte writers shared across the reader commonTest suites — stand-ins for the
 * `java.nio.ByteBuffer` / `java.io.ByteArrayOutputStream` the JVM-side tests reach for. Kept
 * minimal: just the slices the readers actually consume (little-endian ints, ASCII bytes,
 * null-padded fixed-width fields).
 */
internal fun writeIntLe(array: ByteArray, offset: Int, value: Int) {
    array[offset] = (value and 0xFF).toByte()
    array[offset + 1] = ((value shr 8) and 0xFF).toByte()
    array[offset + 2] = ((value shr 16) and 0xFF).toByte()
    array[offset + 3] = ((value shr 24) and 0xFF).toByte()
}

/** Encode an ASCII [text] into [array] at [offset], padding (or truncating) to [width] bytes. */
internal fun writeFixedAscii(array: ByteArray, offset: Int, text: String, width: Int) {
    val bytes = text.encodeToByteArray()
    val len = minOf(bytes.size, width)
    bytes.copyInto(array, offset, 0, len)
    // Trailing bytes default to 0 — that's exactly the null-terminator the readers expect.
    for (i in offset + len until offset + width) array[i] = 0
}

/** Concatenate the given byte arrays in order. */
internal fun cat(vararg parts: ByteArray): ByteArray {
    val total = parts.sumOf { it.size }
    val out = ByteArray(total)
    var pos = 0
    for (p in parts) {
        p.copyInto(out, pos)
        pos += p.size
    }
    return out
}

/** 4-byte little-endian int as a standalone array — handy for chunk-stream builders. */
internal fun intLe(value: Int): ByteArray {
    val out = ByteArray(4)
    writeIntLe(out, 0, value)
    return out
}
