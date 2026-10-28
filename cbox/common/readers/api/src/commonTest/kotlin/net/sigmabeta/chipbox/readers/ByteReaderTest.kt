package net.sigmabeta.chipbox.readers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ByteReaderTest {

    @Test
    fun `reads big-endian shorts ints and longs`() {
        val bytes = cat(shortBe(0x1234), intBe(0x01020304), longBe(0x0102030405060708L))
        val reader = bytesAsReader(bytes)

        assertEquals(0x1234.toShort(), reader.readShortBe())
        assertEquals(0x01020304, reader.readIntBe())
        assertEquals(0x0102030405060708L, reader.readLongBe())
        assertEquals(bytes.size, reader.position())
    }

    @Test
    fun `reads little-endian longs`() {
        val reader = bytesAsReader(longLe(0x0102030405060708L))

        assertEquals(0x0102030405060708L, reader.readLongLe())
        assertEquals(8, reader.position())
    }

    @Test
    fun `sign-extends negative big-endian values`() {
        val reader = bytesAsReader(cat(shortBe(0xFFFF), intBe(-1), longBe(-2L)))

        assertEquals((-1).toShort(), reader.readShortBe())
        assertEquals(-1, reader.readIntBe())
        assertEquals(-2L, reader.readLongBe())
    }

    @Test
    fun `absolute reads are slice-relative and keep the cursor`() {
        val bytes = cat(byteArrayOf(0, 0), shortBe(0x7F80), intBe(0x11223344), longLe(0x0102030405060708L))
        val reader = ByteReader.wrap(bytes, offset = 2, length = bytes.size - 2)

        assertEquals(0x7F80.toShort(), reader.readShortBeAt(0))
        assertEquals(0x11223344, reader.readIntBeAt(2))
        assertEquals(0x0102030405060708L, reader.readLongLeAt(6))
        assertEquals(0, reader.position())
    }

    @Test
    fun `absolute little-endian reads match the cursor reads`() {
        val bytes = cat(shortLe(0x1234), intLe(0x01020304))
        val reader = bytesAsReader(bytes)

        assertEquals(0x1234.toShort(), reader.readShortLeAt(0))
        assertEquals(0x01020304, reader.readIntLeAt(2))
    }

    @Test
    fun `relative reads throw on underflow and leave the cursor alone`() {
        val reader = bytesAsReader(byteArrayOf(1))
        val start = reader.position()

        assertFailsWith<IndexOutOfBoundsException> { reader.readShortBe() }
        assertFailsWith<IndexOutOfBoundsException> { reader.readIntBe() }
        assertFailsWith<IndexOutOfBoundsException> { reader.readLongBe() }
        assertFailsWith<IndexOutOfBoundsException> { reader.readLongLe() }
        assertEquals(start, reader.position())
    }

    @Test
    fun `absolute reads throw when the range falls outside the slice`() {
        val reader = bytesAsReader(ByteArray(6))

        assertFailsWith<IndexOutOfBoundsException> { reader.readIntBeAt(3) }
        assertFailsWith<IndexOutOfBoundsException> { reader.readShortBeAt(-1) }
        assertFailsWith<IndexOutOfBoundsException> { reader.readLongLeAt(0) }
        assertEquals(0, reader.position())
    }

    @Test
    fun `byte array magic matches ascii at an offset`() {
        val bytes = cat(byteArrayOf(0, 0), "STRM".encodeToByteArray())

        assertEquals(true, bytes.hasAsciiAt(2, "STRM"))
        assertEquals(false, bytes.hasAsciiAt(1, "STRM"))
        assertEquals(false, bytes.hasAsciiAt(3, "STRM"))
        assertEquals(false, bytes.hasAsciiAt(-1, "STRM"))
        assertEquals(true, bytes.hasAsciiAt(0, ""))
    }

    @Test
    fun `byte array magic prefix checks the start`() {
        val bytes = "GENH".encodeToByteArray()

        assertEquals(true, bytes.hasAsciiPrefix("GENH"))
        assertEquals(false, bytes.hasAsciiPrefix("GENX"))
        assertEquals(false, bytes.hasAsciiPrefix("GENHh"))
    }

    @Test
    fun `magic checks never throw when the offset would overflow the range check`() {
        val bytes = "GENH".encodeToByteArray()
        val reader = bytesAsReader(bytes)

        // offset + magic.length overflows Int for offsets near Int.MAX_VALUE; the guard must reject
        // the read instead of indexing off the end of the array.
        assertEquals(false, bytes.hasAsciiAt(Int.MAX_VALUE, "GENH"))
        assertEquals(false, reader.hasAsciiAt(Int.MAX_VALUE, "GENH"))
    }

    @Test
    fun `byte reader magic checks are absolute and keep the cursor`() {
        val reader = bytesAsReader("HEADSTRM".encodeToByteArray())

        assertEquals(true, reader.hasAsciiAt(4, "STRM"))
        assertEquals(false, reader.hasAsciiAt(0, "STRM"))
        assertEquals(true, reader.hasAsciiPrefix("HEAD"))
        assertEquals(false, reader.hasAsciiPrefix("STRM"))
        assertEquals(0, reader.position())
    }
}
