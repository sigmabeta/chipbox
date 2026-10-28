package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * EA SCHl (`cbox/native/vgmstream/Core/vgmstream/src/meta/ea_schl.c`,
 * `ea_schl_standard.c`, `layout/blocked_ea_schl.c`).
 *
 * **No real-world SCHl corpus exists on this machine**, so every fixture here is synthetic and the
 * expectations are the arithmetic of `base/play_state.c` (`loop_start + (loop_end - loop_start) * 2
 * + 10 * rate`, else the sample count) followed by the probe's truncating
 * `samples * 1000 / rate`. The single-stream and looping fixtures were additionally fed to the host
 * `libvgmstream` probe, which reported the same `play_samples` (44 100 and 223 502) — parity for the
 * remaining cases is best-effort until real files appear.
 */
class EaSchlReaderTest {

    private val reader = EaSchlReader(BluntHatchet())

    @Test
    fun `a single-stream PCM16 file uses its sample count as the length`() {
        // 44 100 samples at 22 050 Hz = exactly 2 000 ms.
        val track = singleTrack(schlFile(rate = 22_050, samples = 44_100))

        assertEquals(2_000L, track.length)
        assertEquals(0L, track.fadeLengthMs)
    }

    @Test
    fun `streamed tracks use the 1-based convention, unknown tags and OTHER platform`() {
        val track = singleTrack(schlFile(rate = 22_050, samples = 22_050))

        // SCHl's standard header carries no stream name, so the scanner's filename fallback titles it.
        assertEquals(TAG_UNKNOWN, track.title)
        assertEquals(TAG_UNKNOWN, track.artist)
        assertEquals(TAG_UNKNOWN, track.game)
        assertEquals(1, track.trackNumber)
        assertEquals("", track.extension)
        assertEquals(Platform.OTHER, track.platform)
        assertEquals("test.asf", track.path)
    }

    @Test
    fun `a looping stream adds two loop passes and ten fade seconds`() {
        // loop_start 1 000, stored loop_end 2 000 (upstream adds 1 -> 2 001), 22 050 Hz:
        // 1 000 + (2 001 - 1 000) * 2 + 220 500 = 223 502 samples -> 10 136 ms.
        // The host probe reported play_samples = 223 502 for this exact fixture.
        val track = singleTrack(
            schlFile(rate = 22_050, samples = 44_100, loopStart = 1_000, loopEnd = 2_000),
        )

        assertEquals(10_136L, track.length)
        assertEquals(0L, track.fadeLengthMs)
    }

    @Test
    fun `a multi-stream bank numbers its streams one-based`() {
        // Two localized headers (the multi-language SCHl layout): "SHEN" at 22 050 Hz / 44 100
        // samples = 2 000 ms and "SHFR" at 44 100 Hz / 132 300 samples = 3 000 ms.
        val tracks = readTracks(
            multiSchlFile(
                headerBlock(magic = "SHEN", rate = 22_050, samples = 44_100),
                headerBlock(magic = "SHFR", rate = 44_100, samples = 132_300),
            ),
        )

        assertEquals(2, tracks.size)
        assertEquals(1, tracks[0].trackNumber)
        assertEquals(2_000L, tracks[0].length)
        assertEquals(2, tracks[1].trackNumber)
        assertEquals(3_000L, tracks[1].length)
    }

    @Test
    fun `streams whose codec this build cannot decode yield no tracks`() {
        // 0x0F/0x10 (MPEG Layer II/III) and 0x17 (EA Layer 3) sit behind VGM_USE_MPEG; 0x1B
        // (ATRAC3plus) behind VGM_USE_FFMPEG; anything past 0x1B is EAAC. None may claim a length.
        for (codec in listOf(0x0F, 0x10, 0x17, 0x1B, 0x20)) {
            val tracks = reader.readTracksFromFile(schlFile(codec = codec), "excluded.asf")

            assertEquals(emptyList(), tracks, "codec 0x${codec.toString(16)}")
        }
    }

    @Test
    fun `a malformed block table yields no tracks`() {
        // SCCl declares a size past the end of the file.
        assertNull(reader.readTracksFromFile(schlFile(blockSizeOverride = 0xFFFF), "bad.asf"))
        // A block smaller than its own 8-byte header can never advance.
        assertNull(reader.readTracksFromFile(schlFile(blockSizeOverride = 0x04), "bad.asf"))
        // An unknown patch byte makes the header unparseable.
        assertNull(reader.readTracksFromFile(schlFile(unknownPatch = true), "bad.asf"))
        // Truncated to the block id alone.
        assertNull(reader.readTracksFromFile(schlFile().copyOf(4), "bad.asf"))
        // Empty input is a miss, never a throw.
        assertNull(reader.readTracksFromFile(ByteArray(0), "empty.asf"))
    }

    @Test
    fun `a file whose header magic is not SCHl is a miss`() {
        assertNull(reader.readTracksFromFile(schlFile(magic = "NOPE"), "bogus.asf"))
    }

    @Test
    fun `a header whose declared size runs past the file is a miss`() {
        val bytes = schlFile().also { writeIntLe(it, 0x04, 0xFFFF) }

        assertNull(reader.readTracksFromFile(bytes, "bad.asf"))
    }

    @Test
    fun `a malformed later stream is skipped but the good stream still yields a track`() {
        val tracks = readTracks(
            multiSchlFile(
                headerBlock(magic = "SHEN", rate = 22_050, samples = 44_100),
                headerBlock(magic = "SHFR", rate = 22_050, samples = 44_100, unknownPatch = true),
            ),
        )

        assertEquals(1, tracks.size)
        assertEquals(1, tracks[0].trackNumber)
        assertEquals(2_000L, tracks[0].length)
    }

    @Test
    fun `an omitted codec falls back to the platform default - PC v0 PCM16LE`() {
        // PC/v0 defaults: codec1 PCM -> codec2 S16LE_INT. 44 100 samples at 22 050 Hz.
        val track = singleTrack(schlFile(codec = null, rate = 22_050, samples = 44_100))

        assertEquals(2_000L, track.length)
    }

    @Test
    fun `a content-source identifier without an extension still yields OTHER`() {
        // A SAF document id is opaque and has no `.<ext>` suffix; SCHl serves many extensions and
        // none implies a console, so the reader must not parse the identifier for platform.
        val identifier =
            "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Ftrack"

        val tracks = reader.readTracksFromFile(schlFile(rate = 22_050, samples = 22_050), identifier)

        assertEquals(Platform.OTHER, assertNotNull(tracks).single().platform)
    }

    @Test
    fun `a GSTR header is accepted as the generic platform layout`() {
        // GSTR has an extra config word after the id; the rest of the header is the same patches.
        val track = singleTrack(schlFile(platform = null, rate = 48_000, samples = 96_000))

        assertEquals(2_000L, track.length)
    }

    @Test
    fun `every codec on the EA SCHl allow-list yields a track`() {
        // The allow-list itself is pinned to the ticket's scope: PCM8/16 LE/BE, EA-XA and
        // EA-ADPCM/MicroTalk only.
        assertEquals(
            setOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x07, 0x08, 0x09, 0x0A, 0x16),
            StreamedCodecs.ALLOWED_CODECS[StreamedCodecs.Format.EA_SCHL],
        )

        for (codec in StreamedCodecs.ALLOWED_CODECS.getValue(StreamedCodecs.Format.EA_SCHL)) {
            val tracks = reader.readTracksFromFile(schlFile(codec = codec), "allowed.asf")

            assertEquals(1, tracks?.size, "codec 0x${codec.toString(16)}")
        }
    }

    @Test
    fun `decodable EA codecs outside this ticket's scope still yield no tracks`() {
        // VAG (PSX), N64 VADPCM, DVI IMA, GCADPCM and Xbox IMA all compile into the vendored library
        // but are not part of the EA-XA/EA-ADPCM/PCM scope, so they must not claim a length.
        for (codec in listOf(0x05, 0x06, 0x0D, 0x12, 0x14)) {
            val tracks = reader.readTracksFromFile(schlFile(codec = codec), "out-of-scope.asf")

            assertEquals(emptyList(), tracks, "codec 0x${codec.toString(16)}")
        }
    }

    @Test
    fun `the EA SCHl extensions resolve to this reader at version one`() {
        val readers = Readers(BluntHatchet())

        for (extension in listOf("asf", "lasf", "chk", "eam", "exa", "sng", "aud", "sx", "stm", "hab", "xsf")) {
            assertEquals(readers.eaSchl, readers.forExtension(extension), extension)
            assertEquals(1, readers.readerVersionFor(extension), extension)
        }
    }

    @Test
    fun `extensions owned by another reader are not shadowed`() {
        val readers = Readers(BluntHatchet())

        assertEquals(readers.xa, readers.forExtension("xa"))
        assertEquals(readers.strm, readers.forExtension("strm"))
        assertEquals(readers.psf, readers.forExtension("gsf"))
        assertNull(readers.forExtension("snd"))
        assertNull(readers.forExtension("r"))
        assertNull(readers.forExtension("str"))
    }

    private fun singleTrack(bytes: ByteArray): RawTrack {
        val tracks = readTracks(bytes)
        assertEquals(1, tracks.size)
        return tracks[0]
    }

    private fun readTracks(bytes: ByteArray): List<RawTrack> {
        val tracks = reader.readTracksFromFile(bytes, "test.asf")
        assertNotNull(tracks)
        assertTrue(tracks.isNotEmpty())
        return tracks
    }
}

/** EA platform ids (`meta/ea_schl.c:16-36`). */
private const val EA_PLATFORM_PC = 0x00

/** EA codec2 ids used by the fixtures (`meta/ea_schl.c:48-75`). */
private const val EA_CODEC2_S16LE_INT = 0x00

/**
 * A single-stream SCHl file: an `SCHl` header block (patch-encoded fields), then an `SCCl` count
 * block, one `SCDl` data block and the `SCEl` end block — the block chain
 * `layout/blocked_ea_schl.c` walks.
 */
private fun schlFile(
    magic: String = "SCHl",
    rate: Int = 22_050,
    samples: Int = 44_100,
    codec: Int? = EA_CODEC2_S16LE_INT,
    platform: Int? = EA_PLATFORM_PC,
    loopStart: Int? = null,
    loopEnd: Int? = null,
    blockSizeOverride: Int? = null,
    unknownPatch: Boolean = false,
): ByteArray {
    val header = headerBlock(magic, rate, samples, codec, platform, loopStart, loopEnd, unknownPatch)
    val blocks = cat(
        header,
        dataBlocks(samples),
    )
    return if (blockSizeOverride == null) blocks else corruptBlockSize(blocks, header.size, blockSizeOverride)
}

/** A multi-stream SCHl file: the localized header blocks followed by the shared data block chain. */
private fun multiSchlFile(vararg headers: ByteArray): ByteArray = cat(*headers, dataBlocks(44_100))

/** `SCHl`/`SHxx` header block: magic, little-endian size, "PT" + platform, then the patch list. */
private fun headerBlock(
    magic: String,
    rate: Int,
    samples: Int,
    codec: Int? = EA_CODEC2_S16LE_INT,
    platform: Int? = EA_PLATFORM_PC,
    loopStart: Int? = null,
    loopEnd: Int? = null,
    unknownPatch: Boolean = false,
): ByteArray {
    val patches = mutableListOf(
        patch(0x82, 1), // channels
        patch(0x84, rate), // sample rate
        patch(0x85, samples), // sample count
    )
    if (codec != null) patches += patch(0xA0, codec) // codec2
    if (loopStart != null) patches += patch(0x86, loopStart)
    if (loopEnd != null) patches += patch(0x87, loopEnd)
    if (unknownPatch) patches += patch(0x42, 0)

    // "PT" + a little-endian platform id, or "GSTR" + its extra config word.
    val platformEntry = if (platform == null) {
        cat("GSTR".encodeToByteArray(), intBe(0x0100_0000))
    } else {
        cat("PT".encodeToByteArray(), shortLe(platform))
    }
    val body = cat(
        platformEntry,
        *patches.toTypedArray(),
        byteArrayOf(0xFF.toByte()),
    )
    val padded = body.copyOf((body.size + 3) / 4 * 4)
    return cat(
        magic.encodeToByteArray(),
        intLe(8 + padded.size),
        padded,
    )
}

/** Upstream's patch encoding: a type byte, a byte count (1-4), then that many big-endian bytes. */
private fun patch(type: Int, value: Int): ByteArray {
    val bytes = ByteArray(2 + 4)
    bytes[0] = type.toByte()
    bytes[1] = 4
    writeIntBe(bytes, 2, value)
    return bytes
}

/** `SCCl` (one stream, one channel), one `SCDl` data block and the `SCEl` end block. */
private fun dataBlocks(samples: Int): ByteArray = cat(
    block("SCCl", cat(intLe(1), intLe(SAMPLES_BLOCK_SIZE))),
    block("SCDl", cat(intLe(samples), ByteArray(SAMPLES_BLOCK_SIZE))),
    block("SCEl", ByteArray(0)),
)

private fun block(id: String, payload: ByteArray): ByteArray =
    cat(id.encodeToByteArray(), intLe(8 + payload.size), payload)

/** Rewrite the first block size after the header so a fixture can declare a broken chain. */
private fun corruptBlockSize(bytes: ByteArray, headerSize: Int, size: Int): ByteArray {
    val out = bytes.copyOf()
    writeIntLe(out, headerSize + 4, size)
    return out
}

private const val SAMPLES_BLOCK_SIZE = 0x40
