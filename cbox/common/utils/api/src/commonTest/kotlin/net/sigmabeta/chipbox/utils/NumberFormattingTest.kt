package net.sigmabeta.chipbox.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormattingTest {

    @Test
    fun `truncates extra precision to the requested decimal count`() {
        assertEquals("1.23", formatDecimal(1.23456, 2))
    }

    @Test
    fun `rounds half away from zero`() {
        // 1.235 -> 1.24, not 1.23 (no banker's rounding).
        assertEquals("1.24", formatDecimal(1.235, 2))
    }

    @Test
    fun `pads short fractional parts with leading zeros`() {
        // 1.2 with 3 decimals must show "1.200", not "1.2".
        assertEquals("1.200", formatDecimal(1.2, 3))
    }

    @Test
    fun `zero stays zero with the requested precision`() {
        assertEquals("0.00", formatDecimal(0.0, 2))
    }

    @Test
    fun `zero or fewer decimals returns a bare integer string`() {
        assertEquals("1", formatDecimal(1.0, 0))
        assertEquals("2", formatDecimal(1.6, 0))
        assertEquals("2", formatDecimal(1.6, -3))
    }

    @Test
    fun `negative values keep their sign`() {
        assertEquals("-1.50", formatDecimal(-1.5, 2))
    }

    @Test
    fun `tiny negatives that round to zero drop the minus sign`() {
        // -0.0001 rounded to two decimals is 0 — emitting "-0.00" would be misleading.
        assertEquals("0.00", formatDecimal(-0.0001, 2))
    }
}
