package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * FSB5 (FMOD Studio sample bank) metadata reader. Fixtures are synthetic bytes laid out exactly as
 * `cbox/native/vgmstream/Core/vgmstream/src/meta/fsb5.c` reads them: the fixed header, the
 * variable-length per-subsong sample headers (sample-mode u64 + optional extra-flag chain), the
 * per-subsong name-offset table, then the sample data. There is no FSB5 file in the local corpus,
 * so every expectation is a literal derived from the upstream arithmetic in this file.
 */
class Fsb5ReaderTest {

    private val reader = Fsb5Reader(BluntHatchet())

    @Test
    fun `fsb and fsb5 route to the FSB5 reader at version one`() {
        val readers = Readers(BluntHatchet())

        assertSame(readers.fsb5, readers.forExtension("fsb"))
        assertSame(readers.fsb5, readers.forExtension("fsb5"))
        assertEquals(1, readers.readerVersionFor("fsb"))
        assertEquals(1, readers.readerVersionFor("fsb5"))
    }

    @Test
    fun `parses every subsong of a multi-subsong PCM bank with names`() {
        val file = fsb5File(
            subsongs = listOf(
                Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0x00),
                Fsb5SampleSpec(numSamples = 96_000, sampleRateCode = RATE_48000, dataOffset = 0x40),
                Fsb5SampleSpec(
                    numSamples = 44_100,
                    sampleRateCode = RATE_44100,
                    dataOffset = 0x80,
                    loop = Fsb5Loop(start = 1_000, endExclusive = 2_000),
                ),
            ),
            names = listOf("Title One", "Title Two", "Title Three"),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(file, "music/bank.fsb5"))

        assertEquals(3, tracks.size)
        assertEquals(listOf("Title One", "Title Two", "Title Three"), tracks.map { it.title })
        // Streamed readers number subsongs 1-based (see subsongTrackNumber).
        assertEquals(listOf(1, 2, 3), tracks.map { it.trackNumber })
        // 44_100 samples / 44_100 Hz → 1_000 ms; 96_000 / 48_000 → 1_000 ms;
        // looped: 1_000 + (2_000 - 1_000) * 2 + 10 * 44_100 = 444_000 samples → 10_068 ms.
        assertEquals(listOf(1_000L, 2_000L, 10_068L), tracks.map { it.length })
        assertEquals(listOf(0L, 0L, 0L), tracks.map { it.fadeLengthMs })
        assertEquals("music/bank.fsb5", tracks[0].path)
        assertEquals("", tracks[0].source)
        assertEquals(TAG_UNKNOWN, tracks[0].artist)
        assertEquals(TAG_UNKNOWN, tracks[0].game)
        assertEquals(Platform.OTHER, tracks[0].platform)
    }

    @Test
    fun `a bank whose codec needs an external library yields no tracks`() {
        val file = fsb5File(
            subsongs = listOf(Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0)),
            names = listOf("Celeste"),
            codec = CODEC_VORBIS,
        )

        val tracks = assertNotNull(reader.readTracksFromFile(file, "celeste.fsb"))

        // Recognised but unsupported: not a parse failure, so the scanner does not count it failed.
        assertTrue(tracks.isEmpty())
    }

    @Test
    fun `a content-source identifier without an extension still yields OTHER`() {
        // A SAF document id is opaque and has no `.<ext>` suffix; the reader must not parse it to
        // decide the platform (the format carries none anyway).
        val file = fsb5File(
            subsongs = listOf(Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0)),
            names = listOf("Title"),
        )
        val safIdentifier =
            "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fbank"

        val track = assertNotNull(reader.readTracksFromFile(file, safIdentifier)).single()

        assertEquals(Platform.OTHER, track.platform)
        assertEquals(safIdentifier, track.path)
    }

    @Test
    fun `a PCM24 bank yields no tracks because the vendored meta fails that codec`() {
        // Vendored meta/fsb5.c:321-327 logs "found (report)" and goto fail for PCM24/PCM32, so a
        // listed track would never play. The spec's codec decision makes that a no-tracks bank.
        val file = fsb5File(
            subsongs = listOf(Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0)),
            names = listOf("Twenty Four"),
            codec = CODEC_PCM24,
        )

        val tracks = assertNotNull(reader.readTracksFromFile(file, "pcm24.fsb"))

        assertTrue(tracks.isEmpty())
    }

    @Test
    fun `a PCM16 subsong length truncates the samples-to-milliseconds division`() {
        // 1_000 samples at 44_100 Hz = 22.67 ms; the native probe does Long division, so 22 ms.
        val file = fsb5File(
            subsongs = listOf(Fsb5SampleSpec(numSamples = 1_000, sampleRateCode = RATE_44100, dataOffset = 0)),
            names = listOf("Short"),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(file, "short.fsb"))

        assertEquals(22L, tracks.single().length)
    }

    @Test
    fun `a looped subsong plays two loop passes plus the ten-second fade`() {
        // 22_050 Hz, loop 100..10_100: 100 + 10_000 * 2 + 10 * 22_050 = 240_600 samples
        //   → 240_600 * 1000 / 22_050 = 10_911.56 → 10_911 ms.
        val file = fsb5File(
            subsongs = listOf(
                Fsb5SampleSpec(
                    numSamples = 100_000,
                    sampleRateCode = RATE_22050,
                    dataOffset = 0,
                    loop = Fsb5Loop(start = 100, endExclusive = 10_100),
                ),
            ),
            names = listOf("Looped"),
        )

        val track = assertNotNull(reader.readTracksFromFile(file, "looped.fsb")).single()

        assertEquals(10_911L, track.length)
        assertEquals(0L, track.fadeLengthMs)
    }

    @Test
    fun `a whole-file loop is treated as unlooped like vgmstream's autodetect`() {
        // loop_start == 0 and loop_end within 1_152 samples of num_samples: upstream disables the
        // loop ("jingles loops for no reason"), so the length is just the sample count.
        val file = fsb5File(
            subsongs = listOf(
                Fsb5SampleSpec(
                    numSamples = 44_100,
                    sampleRateCode = RATE_44100,
                    dataOffset = 0,
                    loop = Fsb5Loop(start = 0, endExclusive = 44_100),
                ),
            ),
            names = listOf("Jingle"),
        )

        val track = assertNotNull(reader.readTracksFromFile(file, "jingle.fsb")).single()

        assertEquals(1_000L, track.length)
    }

    @Test
    fun `a version 0 bank reads its sample table after the larger base header`() {
        // Version 0 has no flags word and a 0x40-byte base header (vs 0x3C for version 1).
        val file = fsb5File(
            subsongs = listOf(Fsb5SampleSpec(numSamples = 60_000, sampleRateCode = RATE_48000, dataOffset = 0)),
            names = listOf("Legacy"),
            version = 0,
        )

        val track = assertNotNull(reader.readTracksFromFile(file, "legacy.fsb")).single()

        assertEquals("Legacy", track.title)
        // 60_000 samples / 48_000 Hz * 1000 = 1_250 ms.
        assertEquals(1_250L, track.length)
    }

    @Test
    fun `a single unnamed subsong keeps the unknown sentinel for the filename fallback`() {
        val file = fsb5File(
            subsongs = listOf(Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0)),
        )

        val track = assertNotNull(reader.readTracksFromFile(file, "01 Blue Moon.fsb")).single()

        assertEquals(TAG_UNKNOWN, track.title)
        assertEquals(1, track.trackNumber)
    }

    @Test
    fun `a bank without a name table leaves every title unknown`() {
        val file = fsb5File(
            subsongs = listOf(
                Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0x00),
                Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0x40),
            ),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(file, "unnamed.fsb"))

        assertEquals(listOf(TAG_UNKNOWN, TAG_UNKNOWN), tracks.map { it.title })
    }

    @Test
    fun `a zero name-table entry leaves that subsong unknown`() {
        val file = fsb5File(
            subsongs = listOf(
                Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0x00),
                Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0x40),
            ),
            names = listOf(null, "Named"),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(file, "partial.fsb"))

        assertEquals(listOf(TAG_UNKNOWN, "Named"), tracks.map { it.title })
    }

    @Test
    fun `an empty name becomes the unknown sentinel`() {
        val file = fsb5File(
            subsongs = listOf(
                Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0x00),
                Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0x40),
            ),
            names = listOf("", "Named"),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(file, "empty-name.fsb"))

        assertEquals(listOf(TAG_UNKNOWN, "Named"), tracks.map { it.title })
    }

    @Test
    fun `duplicate name offsets share the same title`() {
        // Two subsongs point at the same offset in the name table (8 = the first string, which
        // starts right after the two u32 entries).
        val file = fsb5File(
            subsongs = listOf(
                Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0x00),
                Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0x40),
            ),
            names = listOf("Shared", null),
            nameEntryOverrides = mapOf(1 to 8),
        )

        val tracks = assertNotNull(reader.readTracksFromFile(file, "duplicate.fsb"))

        assertEquals(listOf("Shared", "Shared"), tracks.map { it.title })
    }

    @Test
    fun `a corrupt sample-header table is a null miss`() {
        // The sample-mode bit claims an extra-flag chain but no flag bytes follow, so the walk runs
        // past the sample-header section. Upstream would read past it too; we report a miss.
        val file = fsb5File(
            subsongs = listOf(
                Fsb5SampleSpec(
                    numSamples = 44_100,
                    sampleRateCode = RATE_44100,
                    dataOffset = 0,
                    claimsExtraFlags = true,
                ),
            ),
        )

        assertNull(reader.readTracksFromFile(file, "corrupt-table.fsb"))
    }

    @Test
    fun `an out-of-range name offset is a null miss`() {
        val file = fsb5File(
            subsongs = listOf(Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0)),
            names = listOf("Broken"),
            nameEntryOverrides = mapOf(0 to 0xFFFF),
        )

        assertNull(reader.readTracksFromFile(file, "corrupt-name.fsb"))
    }

    @Test
    fun `an invalid sample-rate code is a null miss`() {
        // Codes 11..15 are rejected by FMOD itself and by the vendored meta ("goto fail").
        val file = fsb5File(
            subsongs = listOf(Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_INVALID, dataOffset = 0)),
        )

        assertNull(reader.readTracksFromFile(file, "bad-rate.fsb"))
    }

    @Test
    fun `a truncated bank is a null miss`() {
        val valid = fsb5File(
            subsongs = listOf(Fsb5SampleSpec(numSamples = 44_100, sampleRateCode = RATE_44100, dataOffset = 0)),
            names = listOf("Truncated"),
        )

        assertNull(reader.readTracksFromFile(valid.copyOf(valid.size - 1), "truncated.fsb"))
    }

    @Test
    fun `a file that is not an FSB5 bank is a null miss`() {
        assertNull(reader.readTracksFromFile(ByteArray(0x40), "not-a-bank.fsb"))
    }
}

private const val CODEC_PCM16 = 0x02
private const val CODEC_PCM24 = 0x03
private const val CODEC_VORBIS = 0x0F
private const val LOOP_EXTRA_FLAG_TYPE = 0x03
private const val LOOP_EXTRA_FLAG_SIZE = 8
private const val RATE_44100 = 8
private const val RATE_48000 = 9
private const val RATE_22050 = 5
private const val RATE_INVALID = 11

/** Loop points as they appear in the FSB5 extra flag: [endExclusive] is stored minus one. */
private data class Fsb5Loop(val start: Int, val endExclusive: Int)

private data class Fsb5SampleSpec(
    val numSamples: Int,
    val sampleRateCode: Int,
    val dataOffset: Int,
    val loop: Fsb5Loop? = null,
    /** Set the sample-mode "has extra flags" bit without emitting a flag — a corrupt table. */
    val claimsExtraFlags: Boolean = false,
)

/**
 * Build a version 1 FSB5 bank whose declared section sizes sum exactly to its length, so it passes
 * the reader's (and upstream's) whole-file size check.
 */
private fun fsb5File(
    subsongs: List<Fsb5SampleSpec>,
    names: List<String?> = emptyList(),
    codec: Int = CODEC_PCM16,
    version: Int = 1,
    nameEntryOverrides: Map<Int, Int> = emptyMap(),
): ByteArray {
    require(names.isEmpty() || names.size == subsongs.size) { "one name entry per subsong" }

    val sampleHeaders = subsongs.map { spec ->
        val hasExtra = spec.loop != null || spec.claimsExtraFlags
        // Sample mode, fsb5.c: 30-bit samples [63..34], 27-bit data offset/32 [33..7],
        // 2-bit channel code [6..5], 4-bit rate code [4..1], extra-flags bit [0].
        val sampleMode = ((spec.numSamples.toLong() and 0x3FFF_FFFFL) shl 34) or
            (((spec.dataOffset.toLong() shr 5) and 0x07FF_FFFFL) shl 7) or
            (spec.sampleRateCode.toLong() shl 1) or
            (if (hasExtra) 1L else 0L)
        val header = longLe(sampleMode)
        val loop = spec.loop ?: return@map header
        val extraFlag = (LOOP_EXTRA_FLAG_TYPE shl 25) or (LOOP_EXTRA_FLAG_SIZE shl 1) or 1
        cat(header, intLe(extraFlag), intLe(loop.start), intLe(loop.endExclusive - 1))
    }
    val sampleHeaderBytes = cat(*sampleHeaders.toTypedArray())

    val nameTable = if (names.isEmpty()) {
        ByteArray(0)
    } else {
        var cursor = 4 * names.size
        val strings = mutableListOf<ByteArray>()
        val entries = IntArray(names.size)
        names.forEachIndexed { index, name ->
            if (name != null) {
                entries[index] = cursor
                val encoded = name.encodeToByteArray() + byteArrayOf(0)
                strings += encoded
                cursor += encoded.size
            }
        }
        nameEntryOverrides.forEach { (index, offset) -> entries[index] = offset }
        cat(*(entries.map { intLe(it) } + strings).toTypedArray())
    }

    val sampleDataSize = subsongs.maxOf { it.dataOffset } + 0x40
    val baseHeaderSize = if (version == 1) 0x3C else 0x40
    val baseHeader = ByteArray(baseHeaderSize)
    writeFixedAscii(baseHeader, 0x00, "FSB5", 4)
    writeIntLe(baseHeader, 0x04, version)
    writeIntLe(baseHeader, 0x08, subsongs.size)
    writeIntLe(baseHeader, 0x0C, sampleHeaderBytes.size)
    writeIntLe(baseHeader, 0x10, nameTable.size)
    writeIntLe(baseHeader, 0x14, sampleDataSize)
    writeIntLe(baseHeader, 0x18, codec)
    if (version == 1) writeIntLe(baseHeader, 0x20, 0)

    return cat(baseHeader, sampleHeaderBytes, nameTable, ByteArray(sampleDataSize))
}
