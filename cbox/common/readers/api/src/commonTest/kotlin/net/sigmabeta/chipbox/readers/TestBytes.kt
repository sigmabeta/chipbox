package net.sigmabeta.chipbox.readers

/**
 * Pure-Kotlin byte writers shared across the reader commonTest suites — stand-ins for the
 * `java.nio.ByteBuffer` / `java.io.ByteArrayOutputStream` the JVM-side tests reach for. Kept
 * minimal: just the slices the readers actually consume (little- and big-endian ints/longs,
 * ASCII bytes, null-padded fixed-width fields).
 */
internal fun writeShortLe(array: ByteArray, offset: Int, value: Int) {
    array[offset] = (value and 0xFF).toByte()
    array[offset + 1] = ((value shr 8) and 0xFF).toByte()
}

internal fun writeShortBe(array: ByteArray, offset: Int, value: Int) {
    array[offset] = ((value shr 8) and 0xFF).toByte()
    array[offset + 1] = (value and 0xFF).toByte()
}

internal fun writeIntLe(array: ByteArray, offset: Int, value: Int) {
    array[offset] = (value and 0xFF).toByte()
    array[offset + 1] = ((value shr 8) and 0xFF).toByte()
    array[offset + 2] = ((value shr 16) and 0xFF).toByte()
    array[offset + 3] = ((value shr 24) and 0xFF).toByte()
}

internal fun writeIntBe(array: ByteArray, offset: Int, value: Int) {
    array[offset] = ((value shr 24) and 0xFF).toByte()
    array[offset + 1] = ((value shr 16) and 0xFF).toByte()
    array[offset + 2] = ((value shr 8) and 0xFF).toByte()
    array[offset + 3] = (value and 0xFF).toByte()
}

internal fun writeLongLe(array: ByteArray, offset: Int, value: Long) {
    for (i in 0 until 8) array[offset + i] = ((value shr (8 * i)) and 0xFF).toByte()
}

internal fun writeLongBe(array: ByteArray, offset: Int, value: Long) {
    for (i in 0 until 8) array[offset + i] = ((value shr (8 * (7 - i))) and 0xFF).toByte()
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

/** 2-byte little-endian short as a standalone array. */
internal fun shortLe(value: Int): ByteArray = ByteArray(2).also { writeShortLe(it, 0, value) }

/** 2-byte big-endian short as a standalone array. */
internal fun shortBe(value: Int): ByteArray = ByteArray(2).also { writeShortBe(it, 0, value) }

/** 4-byte little-endian int as a standalone array — handy for chunk-stream builders. */
internal fun intLe(value: Int): ByteArray = ByteArray(4).also { writeIntLe(it, 0, value) }

/** 4-byte big-endian int as a standalone array. */
internal fun intBe(value: Int): ByteArray = ByteArray(4).also { writeIntBe(it, 0, value) }

/** 8-byte little-endian long as a standalone array. */
internal fun longLe(value: Long): ByteArray = ByteArray(8).also { writeLongLe(it, 0, value) }

/** 8-byte big-endian long as a standalone array. */
internal fun longBe(value: Long): ByteArray = ByteArray(8).also { writeLongBe(it, 0, value) }
