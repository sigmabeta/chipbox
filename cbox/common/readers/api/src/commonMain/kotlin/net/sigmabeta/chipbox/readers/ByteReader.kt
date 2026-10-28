package net.sigmabeta.chipbox.readers

/**
 * `0xFFFF_FFFF` as a [Long], for widening a header's 32-bit unsigned field (a signed [Int] read from
 * a container) before it feeds a length or offset computation. One shared constant so the streamed
 * readers cannot disagree about it.
 */
internal const val UINT32_MASK = 0xFFFF_FFFFL

/**
 * Minimal random-access reader over a [ByteArray] — a multiplatform stand-in for the
 * `java.nio.ByteBuffer` the format readers used. Exposes only the slice of the ByteBuffer API
 * those readers need (positioned reads, seek, bulk get), so they port over near-mechanically.
 *
 * **Endianness is part of every integer method's name** (`readIntLe` / `readIntBe`): a header
 * parser must never have to guess, because the two are indistinguishable at the call site.
 *
 * **Underflow semantics are deliberate and split by method shape:**
 * - [readBytes] is *soft*: it returns `null` when fewer than `count` bytes remain and does not move
 *   the cursor, for speculative reads.
 * - Every fixed-width read ([readShortLe]/[readShortBe]/[readIntLe]/[readIntBe]/[readLongLe]/
 *   [readLongBe]), the byte cursor reads ([get]) and every absolute read (`…At`) is *hard*: it
 *   throws [IndexOutOfBoundsException] on underflow / out-of-range and leaves the cursor untouched.
 *   Readers rely on catching that exception (or their own pre-flight length checks).
 *
 * Absolute (`…At`) reads take an index relative to the slice start, never move the cursor, and are
 * the way to inspect a header field without disturbing a sequential parse.
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
        requireAvailable(dest.size)
        bytes.copyInto(dest, 0, pos, pos + dest.size)
        pos += dest.size
    }

    /** Read a little-endian signed 16-bit value and advance; throws on underflow. */
    fun readShortLe(): Short {
        requireAvailable(2)
        return shortAt(pos, littleEndian = true).also { pos += 2 }
    }

    /** Read a big-endian signed 16-bit value and advance; throws on underflow. */
    fun readShortBe(): Short {
        requireAvailable(2)
        return shortAt(pos, littleEndian = false).also { pos += 2 }
    }

    /** Read a little-endian signed 32-bit int and advance; throws on underflow. */
    fun readIntLe(): Int {
        requireAvailable(4)
        return intAt(pos, littleEndian = true).also { pos += 4 }
    }

    /** Read a big-endian signed 32-bit int and advance; throws on underflow. */
    fun readIntBe(): Int {
        requireAvailable(4)
        return intAt(pos, littleEndian = false).also { pos += 4 }
    }

    /** Read a little-endian signed 64-bit long and advance; throws on underflow. */
    fun readLongLe(): Long {
        requireAvailable(8)
        return longAt(pos, littleEndian = true).also { pos += 8 }
    }

    /** Read a big-endian signed 64-bit long and advance; throws on underflow. */
    fun readLongBe(): Long {
        requireAvailable(8)
        return longAt(pos, littleEndian = false).also { pos += 8 }
    }

    /** Little-endian signed 16-bit value at absolute [index]; never moves the cursor. */
    fun readShortLeAt(index: Int): Short {
        requireInSlice(index, 2)
        return shortAt(start + index, littleEndian = true)
    }

    /** Big-endian signed 16-bit value at absolute [index]; never moves the cursor. */
    fun readShortBeAt(index: Int): Short {
        requireInSlice(index, 2)
        return shortAt(start + index, littleEndian = false)
    }

    /** Little-endian signed 32-bit int at absolute [index]; never moves the cursor. */
    fun readIntLeAt(index: Int): Int {
        requireInSlice(index, 4)
        return intAt(start + index, littleEndian = true)
    }

    /** Big-endian signed 32-bit int at absolute [index]; never moves the cursor. */
    fun readIntBeAt(index: Int): Int {
        requireInSlice(index, 4)
        return intAt(start + index, littleEndian = false)
    }

    /** Little-endian signed 64-bit long at absolute [index]; never moves the cursor. */
    fun readLongLeAt(index: Int): Long {
        requireInSlice(index, 8)
        return longAt(start + index, littleEndian = true)
    }

    /** Big-endian signed 64-bit long at absolute [index]; never moves the cursor. */
    fun readLongBeAt(index: Int): Long {
        requireInSlice(index, 8)
        return longAt(start + index, littleEndian = false)
    }

    /** Single byte at the current position, advancing (mirrors `ByteBuffer.get()`). */
    fun get(): Byte {
        requireAvailable(1)
        return bytes[pos++]
    }

    /** Byte at absolute [index] (relative to the slice start), without moving the position. */
    fun get(index: Int): Byte {
        requireInSlice(index, 1)
        return bytes[start + index]
    }

    /** This slice's bytes as a standalone array (the old `ByteBuffer.array()`). */
    fun array(): ByteArray = bytes.copyOfRange(start, end)

    private fun requireAvailable(count: Int) {
        if (pos + count > end) throw IndexOutOfBoundsException("ByteReader underflow")
    }

    private fun requireInSlice(index: Int, count: Int) {
        if (index < 0 || index + count > size) {
            throw IndexOutOfBoundsException("ByteReader index $index")
        }
    }

    private fun shortAt(absolute: Int, littleEndian: Boolean): Short {
        val first = bytes[absolute].toInt() and 0xFF
        val second = bytes[absolute + 1].toInt() and 0xFF
        val value = if (littleEndian) first or (second shl 8) else (first shl 8) or second
        return value.toShort()
    }

    private fun intAt(absolute: Int, littleEndian: Boolean): Int {
        val b0 = bytes[absolute].toInt() and 0xFF
        val b1 = bytes[absolute + 1].toInt() and 0xFF
        val b2 = bytes[absolute + 2].toInt() and 0xFF
        val b3 = bytes[absolute + 3].toInt() and 0xFF
        return if (littleEndian) {
            b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        } else {
            (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }
    }

    private fun longAt(absolute: Int, littleEndian: Boolean): Long {
        var value = 0L
        if (littleEndian) {
            for (i in 7 downTo 0) {
                value = (value shl 8) or (bytes[absolute + i].toLong() and 0xFF)
            }
        } else {
            for (i in 0 until 8) {
                value = (value shl 8) or (bytes[absolute + i].toLong() and 0xFF)
            }
        }
        return value
    }

    companion object {
        fun wrap(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): ByteReader =
            ByteReader(bytes, offset, offset + length)
    }
}
