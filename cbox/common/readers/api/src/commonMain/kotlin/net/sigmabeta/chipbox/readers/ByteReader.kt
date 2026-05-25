package net.sigmabeta.chipbox.readers

/**
 * Minimal little-endian, random-access reader over a [ByteArray] — a multiplatform stand-in for
 * the `java.nio.ByteBuffer` the format readers used. Exposes only the slice of the ByteBuffer API
 * those readers need (positioned reads, seek, bulk get), so they port over near-mechanically.
 *
 * Reads that run past the end either return null ([readBytes]) or throw
 * [IndexOutOfBoundsException] ([get]/[readIntLe]) — mirroring `ByteBuffer.get`'s underflow
 * behaviour the readers already catch.
 */
internal class ByteReader private constructor(
    private val bytes: ByteArray,
    private val start: Int,
    private val end: Int, // exclusive
) {
    private var pos: Int = start

    /** Current read position, relative to the slice start. */
    fun position(): Int = pos - start

    /** Seek to [newPosition] (relative to the slice start). */
    fun position(newPosition: Int): ByteReader {
        pos = start + newPosition
        return this
    }

    /** Total length of this reader's slice (the old `ByteBuffer.array().size`). */
    val size: Int get() = end - start

    /** Read [count] bytes and advance; null if fewer than [count] remain (no advance). */
    fun readBytes(count: Int): ByteArray? {
        if (count < 0 || pos + count > end) return null
        val out = bytes.copyOfRange(pos, pos + count)
        pos += count
        return out
    }

    /** Bulk read into [dest], advancing by its size; throws on underflow. */
    fun get(dest: ByteArray) {
        if (pos + dest.size > end) throw IndexOutOfBoundsException("ByteReader underflow")
        bytes.copyInto(dest, 0, pos, pos + dest.size)
        pos += dest.size
    }

    /** Read a little-endian signed 32-bit int and advance; throws on underflow. */
    fun readIntLe(): Int {
        if (pos + 4 > end) throw IndexOutOfBoundsException("ByteReader underflow")
        val value = (bytes[pos].toInt() and 0xFF) or
            ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
            ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
            ((bytes[pos + 3].toInt() and 0xFF) shl 24)
        pos += 4
        return value
    }

    /** Read a little-endian signed 16-bit value and advance; throws on underflow. */
    fun readShortLe(): Short {
        if (pos + 2 > end) throw IndexOutOfBoundsException("ByteReader underflow")
        val value = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
        pos += 2
        return value.toShort()
    }

    /** Single byte at the current position, advancing (mirrors `ByteBuffer.get()`). */
    fun get(): Byte {
        if (pos >= end) throw IndexOutOfBoundsException("ByteReader underflow")
        return bytes[pos++]
    }

    /** Byte at absolute [index] (relative to the slice start), without moving the position. */
    fun get(index: Int): Byte {
        val i = start + index
        if (i < start || i >= end) throw IndexOutOfBoundsException("ByteReader index $index")
        return bytes[i]
    }

    /** This slice's bytes as a standalone array (the old `ByteBuffer.array()`). */
    fun array(): ByteArray = bytes.copyOfRange(start, end)

    companion object {
        fun wrap(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): ByteReader =
            ByteReader(bytes, offset, offset + length)
    }
}
