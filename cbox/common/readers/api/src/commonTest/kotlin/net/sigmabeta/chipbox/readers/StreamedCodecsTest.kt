package net.sigmabeta.chipbox.readers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.sigmabeta.chipbox.readers.StreamedCodecs.Format

/**
 * Pins the streamed codec allow-lists to the **vendored build** — the authority for what Chipbox
 * can actually play. FSB5 comes from the `switch (fsb5.codec)` block in
 * `cbox/native/vgmstream/Core/vgmstream/src/meta/fsb5.c`, with its `#ifdef VGM_USE_*` cases
 * compiled out by `CMakeLists.txt` (`VGM_LOG_OUTPUT` only) and `src/vgmstream.h:6-18` (all
 * `VGM_USE_*` commented out). PCM24/PCM32 are rejected because the vendored `fsb5.c` logs and
 * `goto fail`s them.
 */
class StreamedCodecsTest {

    @Test
    fun `the FSB5 allow-list is exactly the codecs the vendored meta decodes`() {
        assertEquals(
            setOf(0x01, 0x02, 0x05, 0x06, 0x07, 0x08, 0x09, 0x10),
            StreamedCodecs.ALLOWED_CODECS[Format.FSB5],
        )
    }

    @Test
    fun `every codec the vendored build decodes is accepted`() {
        // PCM8, PCM16, PCMFLOAT, GCADPCM, IMA ADPCM, VAG, HEVAG, FADPCM.
        val allowed = listOf(0x01, 0x02, 0x05, 0x06, 0x07, 0x08, 0x09, 0x10)
        for (codec in allowed) {
            assertTrue(
                StreamedCodecs.isCodecAllowed(Format.FSB5, codec),
                "codec 0x${codec.toString(16)} should be decodable by the vendored build",
            )
        }
    }

    @Test
    fun `codecs the vendored meta fails and external-codec ids are rejected`() {
        // 0x03/0x04 = PCM24/PCM32: vendored fsb5.c logs "found (report)" and goto fail.
        // 0x00 = NONE; 0x0A..0x0F = XMA, MPEG, CELT, ATRAC9, XWMA, Vorbis; 0x11 = Opus.
        val rejected = listOf(0x00, 0x03, 0x04, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x11)
        for (codec in rejected) {
            assertFalse(
                StreamedCodecs.isCodecAllowed(Format.FSB5, codec),
                "codec 0x${codec.toString(16)} cannot be decoded by the vendored build",
            )
        }
        // Anything newer / out of range is rejected too (never assume forward compatibility).
        for (codec in listOf(0x12, 0x20, 0x7F, 0xFF, -1)) {
            assertFalse(
                StreamedCodecs.isCodecAllowed(Format.FSB5, codec),
                "unknown codec $codec must be rejected",
            )
        }
    }

    @Test
    fun `the GENH allow-list is exactly the built-in genh cases`() {
        // meta/genh.c's switch minus cases behind VGM_USE_MPEG (8), VGM_USE_FFMPEG (18..23) and
        // AAC (28).
        assertEquals(
            setOf(0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 24, 25, 26, 27),
            StreamedCodecs.ALLOWED_CODECS[Format.GENH],
        )
    }

    @Test
    fun `the consolidated map rejects a codec that belongs to another format's list`() {
        assertTrue(StreamedCodecs.isCodecAllowed(Format.FSB5, 0x10))
        assertFalse(StreamedCodecs.isCodecAllowed(Format.EA_SCHL, 0x10), "0x10 is FSB5 FADPCM, not EA MPEG")
        assertTrue(StreamedCodecs.isCodecAllowed(Format.EA_SCHL, 0x16))
        assertFalse(StreamedCodecs.isCodecAllowed(Format.FSB5, 0x16))
    }
}
