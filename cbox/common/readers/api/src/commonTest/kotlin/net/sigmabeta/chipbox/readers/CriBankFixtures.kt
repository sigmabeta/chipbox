package net.sigmabeta.chipbox.readers

/**
 * Synthetic CRI bank fixtures shared by the AWB and ACB reader tests.
 *
 * There is no `.awb`/`.acb`/`.acx` file in the local corpus, so these builders lay bytes out exactly
 * as upstream reads them: an AFS2 wave bank (`meta/awb.c`), an embedded HCA cue
 * (`coding/libs/clhca.c`, including the trailing CRC-16) and an embedded ADX cue (`meta/adx.c`),
 * plus the CRI `@UTF` tables an ACB cue sheet uses (`util/cri_utf.c`). Every expected value in the
 * tests is an independent literal computed from those layouts, never from the reader's output.
 */

// --- HCA cue ------------------------------------------------------------------------------------

private const val HCA_FIXTURE_BASE_SIZE = 0x08
private const val HCA_FIXTURE_FMT_SIZE = 0x10
private const val HCA_FIXTURE_COMP_SIZE = 0x10
private const val HCA_FIXTURE_CRC_SIZE = 2

/** One synthetic HCA stream: a v1.1 stereo header with `blockCount` 1024-sample blocks. */
internal fun hcaCue(blockCount: Int = 44, sampleRate: Int = 44_100): ByteArray {
    val base = ByteArray(HCA_FIXTURE_BASE_SIZE)
    writeFixedAscii(base, 0x00, "HCA\u0000", 4)
    writeShortBe(base, 0x04, 0x0101)

    val fmt = ByteArray(HCA_FIXTURE_FMT_SIZE)
    writeFixedAscii(fmt, 0x00, "fmt\u0000", 4)
    fmt[0x04] = 2 // channels
    writeU24Be(fmt, 0x05, sampleRate)
    writeIntBe(fmt, 0x08, blockCount)

    val comp = ByteArray(HCA_FIXTURE_COMP_SIZE)
    writeFixedAscii(comp, 0x00, "comp", 4)
    writeShortBe(comp, 0x04, 0x20) // frame size
    comp[0x06] = 1 // min resolution
    comp[0x07] = 15 // max resolution
    comp[0x0A] = 128.toByte() // total bands
    comp[0x0B] = 128.toByte() // base bands

    val body = cat(base, fmt, comp)
    // `header_size` covers the chunks plus the trailing CRC-16.
    writeShortBe(body, 0x06, body.size + HCA_FIXTURE_CRC_SIZE)
    return cat(body, shortBe(hcaCrc16(body, 0, body.size)), ByteArray(0x20))
}

private fun writeU24Be(array: ByteArray, offset: Int, value: Int) {
    array[offset] = ((value shr 16) and 0xFF).toByte()
    array[offset + 1] = ((value shr 8) and 0xFF).toByte()
    array[offset + 2] = (value and 0xFF).toByte()
}

// --- ADX cue ------------------------------------------------------------------------------------

private const val ADX_FIXTURE_DATA_START = 0x100

/** One synthetic plain v0x0400 ADX cue with no loop block, followed by zeroed audio. */
internal fun adxCue(numSamples: Int = 44_100, sampleRate: Int = 44_100): ByteArray =
    ByteArray(ADX_FIXTURE_DATA_START + 0x20).also { bytes ->
        writeShortBe(bytes, 0x00, 0x8000)
        writeShortBe(bytes, 0x02, ADX_FIXTURE_DATA_START - 4)
        bytes[0x04] = 3 // encoding type
        bytes[0x05] = 0x12 // frame size
        bytes[0x06] = 4 // bits per sample
        bytes[0x07] = 2 // channels
        writeIntBe(bytes, 0x08, sampleRate)
        writeIntBe(bytes, 0x0C, numSamples)
        writeShortBe(bytes, 0x10, 500)
        writeShortBe(bytes, 0x12, 0x0400)
        writeFixedAscii(bytes, ADX_FIXTURE_DATA_START - 6, "(c)CRI", 6)
    }

// --- AWB wave bank ------------------------------------------------------------------------------

/** One wave in an AWB fixture: its id-table value and the bytes of its subfile. */
internal data class AwbCueFixture(val waveId: Int, val audio: ByteArray)

/**
 * Build a standalone AFS2/AWB bank. Offsets in the table are relative to the bank start, exactly
 * like the native streamfile upstream parses, and the final table entry is the bank end.
 */
internal fun awb(
    cues: List<AwbCueFixture>,
    offsetSize: Int = 4,
    offsetAlignment: Int = 4,
): ByteArray {
    val idTableSize = cues.size * 2
    val offsetTable = 0x10 + idTableSize
    val offsetTableSize = (cues.size + 1) * offsetSize
    val headerEnd = alignUp(0x10 + idTableSize + offsetTableSize, offsetAlignment)

    val starts = mutableListOf<Int>()
    var position = headerEnd
    for (cue in cues) {
        starts += position
        position = alignUp(position + cue.audio.size, offsetAlignment)
    }

    val bytes = ByteArray(position)
    writeFixedAscii(bytes, 0x00, "AFS2", 4)
    bytes[0x04] = 1 // version
    bytes[0x05] = offsetSize.toByte()
    writeShortLe(bytes, 0x06, 2) // wave-id alignment
    writeIntLe(bytes, 0x08, cues.size)
    writeShortLe(bytes, 0x0C, offsetAlignment)
    writeShortLe(bytes, 0x0E, 0) // subkey

    cues.forEachIndexed { index, cue -> writeShortLe(bytes, 0x10 + index * 2, cue.waveId) }
    (starts + position).forEachIndexed { index, offset ->
        val entry = offsetTable + index * offsetSize
        if (offsetSize == 4) writeIntLe(bytes, entry, offset) else writeShortLe(bytes, entry, offset)
    }
    cues.forEachIndexed { index, cue -> cue.audio.copyInto(bytes, starts[index]) }
    return bytes
}

private fun alignUp(value: Int, alignment: Int): Int {
    val remainder = value % alignment
    return if (remainder == 0) value else value + (alignment - remainder)
}

// --- ACB cue sheet ------------------------------------------------------------------------------

/** One ACB cue: its name, the wave id its waveform row points at, and the reference type. */
internal data class AcbCueFixture(
    val name: String,
    val waveId: Int,
    val referenceType: Int = 1,
    /** Row of `CueTable` this name points at; defaults to the cue's own row. */
    val cueIndex: Int? = null,
)

/**
 * Build an ACB cue sheet: a `Header` @UTF table whose `AwbFile` column embeds [awb] (or an empty
 * blob for a cue sheet that only references an external bank) plus the cue/name/waveform tables.
 */
internal fun acb(awb: ByteArray?, cues: List<AcbCueFixture>): ByteArray {
    val cueNameTable = utfTable(
        name = "CueNameTable",
        columns = listOf("CueIndex" to UTF_U16, "CueName" to UTF_STRING),
        rows = cues.mapIndexed { index, cue -> listOf(UtfNum(cue.cueIndex ?: index), UtfText(cue.name)) },
    )
    val cueTable = utfTable(
        name = "CueTable",
        columns = listOf("ReferenceType" to UTF_U8, "ReferenceIndex" to UTF_U16),
        rows = cues.mapIndexed { index, cue -> listOf(UtfNum(cue.referenceType), UtfNum(index)) },
    )
    val waveformTable = utfTable(
        name = "WaveformTable",
        columns = listOf("Id" to UTF_U16, "Streaming" to UTF_U8),
        rows = cues.map { cue -> listOf(UtfNum(cue.waveId), UtfNum(0)) },
    )
    return utfTable(
        name = "Header",
        columns = listOf(
            "AwbFile" to UTF_VLDATA,
            "WaveformTable" to UTF_VLDATA,
            "CueTable" to UTF_VLDATA,
            "CueNameTable" to UTF_VLDATA,
        ),
        rows = listOf(
            listOf(
                UtfBlob(awb ?: ByteArray(0)),
                UtfBlob(waveformTable),
                UtfBlob(cueTable),
                UtfBlob(cueNameTable),
            ),
        ),
    )
}

private const val UTF_U8 = 0x00
private const val UTF_U16 = 0x02
private const val UTF_STRING = 0x0A
private const val UTF_VLDATA = 0x0B
private const val UTF_FLAG_NAME = 0x10
private const val UTF_FLAG_ROW = 0x40
private const val UTF_HEADER_SIZE = 0x20

private sealed interface UtfCell

private class UtfNum(val value: Int) : UtfCell

private class UtfText(val value: String) : UtfCell

private class UtfBlob(val value: ByteArray) : UtfCell

/**
 * Minimal CRI `@UTF` table writer: row-only columns, `uint8`/`uint16`/`string`/`vlData` types.
 * Offsets in the header are stored minus 8, as upstream adds 8 back on read.
 */
private fun utfTable(
    name: String,
    columns: List<Pair<String, Int>>,
    rows: List<List<UtfCell>>,
): ByteArray {
    val stringParts = mutableListOf<ByteArray>()
    val stringOffsets = mutableMapOf<String, Int>()
    var stringSize = 0

    fun addString(text: String): Int = stringOffsets.getOrPut(text) {
        stringSize.also {
            val encoded = text.encodeToByteArray() + 0
            stringParts += encoded
            stringSize += encoded.size
        }
    }

    val nameOffset = addString(name)
    columns.forEach { addString(it.first) }
    rows.forEach { row -> row.filterIsInstance<UtfText>().forEach { addString(it.value) } }

    val schema = ByteArray(columns.size * 5)
    columns.forEachIndexed { index, (columnName, type) ->
        schema[index * 5] = (UTF_FLAG_NAME or UTF_FLAG_ROW or type).toByte()
        writeIntBe(schema, index * 5 + 1, stringOffsets.getValue(columnName))
    }

    val rowWidth = columns.sumOf { typeSize(it.second) }
    val rowBytes = ByteArray(rows.size * rowWidth)
    val blobs = mutableListOf<ByteArray>()
    var blobSize = 0
    rows.forEachIndexed { rowIndex, row ->
        var slot = 0
        row.forEachIndexed { columnIndex, cell ->
            val type = columns[columnIndex].second
            val position = rowIndex * rowWidth + slot
            when (cell) {
                is UtfNum -> if (type == UTF_U16) {
                    writeShortBe(rowBytes, position, cell.value)
                } else {
                    rowBytes[position] = cell.value.toByte()
                }

                is UtfText -> writeIntBe(rowBytes, position, stringOffsets.getValue(cell.value))

                is UtfBlob -> {
                    writeIntBe(rowBytes, position, blobSize)
                    writeIntBe(rowBytes, position + 4, cell.value.size)
                    blobs += cell.value
                    blobSize += cell.value.size
                }
            }
            slot += typeSize(type)
        }
    }

    val rowsOffset = UTF_HEADER_SIZE + schema.size
    val stringsOffset = rowsOffset + rowBytes.size
    val dataOffset = stringsOffset + stringSize
    val tableSize = dataOffset + blobSize

    val out = ByteArray(tableSize)
    writeFixedAscii(out, 0x00, "@UTF", 4)
    writeIntBe(out, 0x04, tableSize - 8)
    writeShortBe(out, 0x08, 1) // version
    writeShortBe(out, 0x0A, rowsOffset - 8)
    writeIntBe(out, 0x0C, stringsOffset - 8)
    writeIntBe(out, 0x10, dataOffset - 8)
    writeIntBe(out, 0x14, nameOffset)
    writeShortBe(out, 0x18, columns.size)
    writeShortBe(out, 0x1A, rowWidth)
    writeIntBe(out, 0x1C, rows.size)
    schema.copyInto(out, UTF_HEADER_SIZE)
    rowBytes.copyInto(out, rowsOffset)

    var position = stringsOffset
    stringParts.forEach {
        it.copyInto(out, position)
        position += it.size
    }
    position = dataOffset
    blobs.forEach {
        it.copyInto(out, position)
        position += it.size
    }
    return out
}

private fun typeSize(type: Int): Int = when (type) {
    UTF_U8 -> 1
    UTF_U16 -> 2
    UTF_STRING -> 4
    UTF_VLDATA -> 8
    else -> error("unsupported fixture column type $type")
}
