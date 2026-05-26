package net.sigmabeta.chipbox.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteArrayExtensionsTest {

    @Test
    fun `convert decodes ASCII bytes as Latin-1`() {
        assertEquals("Hello", byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F).convert())
    }

    @Test
    fun `convert preserves bytes in the upper half of Latin-1`() {
        // 0xFF maps to U+00FF ("ÿ") — the whole point of the Latin-1 path is that high bytes
        // round-trip 1:1 instead of being mangled into the UTF-8 replacement character.
        assertEquals("ÿ", byteArrayOf(0xFF.toByte()).convert())
        assertEquals("É", byteArrayOf(0xC9.toByte()).convert())
    }

    @Test
    fun `convert on an empty array returns the empty string`() {
        assertEquals("", byteArrayOf().convert())
    }

    @Test
    fun `convertUtf decodes ASCII bytes`() {
        assertEquals("Hi", byteArrayOf(0x48, 0x69).convertUtf())
    }

    @Test
    fun `convertUtf decodes multi-byte UTF-8 sequences`() {
        // "é" in UTF-8 is 0xC3 0xA9 — Latin-1 would have read this as "Ã©" instead.
        assertEquals("é", byteArrayOf(0xC3.toByte(), 0xA9.toByte()).convertUtf())
    }

    @Test
    fun `convert and convertUtf disagree on bytes above 0x7F`() {
        // Same input, two decoders, two answers — guards against silently swapping one for the
        // other in code that depends on the Latin-1 behavior (e.g. PSF tag decoding).
        val bytes = byteArrayOf(0xC3.toByte(), 0xA9.toByte())
        assertEquals("Ã©", bytes.convert())
        assertEquals("é", bytes.convertUtf())
    }
}
