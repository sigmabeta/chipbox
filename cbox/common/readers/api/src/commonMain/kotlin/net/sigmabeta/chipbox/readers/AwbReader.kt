package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * CRI AFS2/AWB wave bank (`.awb`, `.afs2`) — the streaming wave bank of CRI Atom/ADX2 games.
 *
 * Ports the container parse from `cbox/native/vgmstream/Core/vgmstream/src/meta/awb.c`: the `AFS2`
 * header, the wave-id table and the absolute subfile-offset table, with upstream's alignment
 * padding on each boundary. Each cue is then handed to the codec's header reader — [readHcaHeader]
 * or [readAdxHeader] — for its length under the shared streamed-audio loop policy. This is the same
 * per-cue (`subsong`) model the native probe enumerates, so [RawTrack.trackNumber] stays 1-based and
 * aligned with the wave index.
 *
 * **Scope:** a wave bank contains no cue names; a sibling `.acb` cue sheet supplies them (see
 * [AcbReader]). A cue whose payload is not HCA/ADX (VAG, RIFF, DSP, Opus, …) is skipped rather than
 * guessed at, and keeps its wave index so the other cues' track numbers do not shift. Only metadata
 * is read; playback still decodes through `libvgmstream`.
 */
class AwbReader(private val hatchet: Hatchet) : Reader() {
    override val version: Int = AWB_VERSION

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        if (!bytes.hasAsciiPrefix(AFS2_MAGIC)) {
            hatchet.w("AWB parse failed: $identifier is not an AFS2 wave bank.")
            null
        } else {
            readBank(bytes, identifier)
        }

    private fun readBank(bytes: ByteArray, identifier: String): List<RawTrack> {
        val bank = readAwbBank(bytes, base = 0, size = bytes.size)
        if (bank == null || bank.waves.isEmpty()) {
            hatchet.w("AWB skipped: $identifier has no readable cue (malformed or unsupported header).")
            return emptyList()
        }
        val skipped = bank.declaredCount - bank.waves.size
        if (skipped > 0) {
            hatchet.w("AWB: $identifier skipped $skipped of ${bank.declaredCount} cue(s).")
        }
        return bank.waves.map { wave ->
            streamedRawTrack(identifier, wave.lengthMs, subsongIndex = wave.index)
        }
    }

    companion object {
        const val AWB_VERSION = 1
    }
}

/** One readable cue inside an AWB: its 0-based wave index, id-table value and probe length. */
internal data class AwbWave(
    val index: Int,
    val waveId: Int,
    val lengthMs: Long,
)

/**
 * A parsed wave bank. [declaredCount] is the header's cue count; [waves] only contains the cues whose
 * payload yielded a length, so `declaredCount - waves.size` is the skipped count.
 */
internal data class AwbBank(
    val waves: List<AwbWave>,
    val declaredCount: Int,
)

/**
 * Parses an AWB starting at [base] and limited to [size] bytes — a whole `.awb` file (`base = 0`,
 * `size = bytes.size`) or the `AwbFile` blob embedded in an ACB. All table offsets are relative to
 * [base], matching upstream's subfile streamfile. Returns null when the container is not an AFS2
 * bank or its header is unusable; unreadable individual cues are dropped, never thrown.
 */
internal fun readAwbBank(bytes: ByteArray, base: Int, size: Int): AwbBank? {
    val header = readAwbHeader(bytes, base, size) ?: return null
    val waves = header.entries.mapNotNull { entry -> readAwbWave(bytes, base, entry) }
    return AwbBank(waves, header.declaredCount)
}

private data class AwbEntry(val index: Int, val waveId: Int, val offset: Int, val end: Int)

private data class AwbHeader(val declaredCount: Int, val entries: List<AwbEntry>)

private fun readAwbHeader(bytes: ByteArray, base: Int, size: Int): AwbHeader? =
    try {
        parseAwbHeader(bytes, base, size)
    } catch (e: IndexOutOfBoundsException) {
        // Defensive: every field below is pre-flighted, but a miss is still preferable to a throw.
        null
    }

private fun parseAwbHeader(bytes: ByteArray, base: Int, size: Int): AwbHeader? {
    val inBounds = base >= 0 && size >= AWB_HEADER_SIZE && base.toLong() + size <= bytes.size
    if (!inBounds || !bytes.hasAsciiAt(base, AFS2_MAGIC)) return null

    val reader = ByteReader.wrap(bytes, base, size)
    val offsetSize = reader.get(0x05).toInt() and 0xFF
    val waveIdAlignment = reader.readShortLeAt(0x06).toInt() and 0xFFFF
    val declaredCount = reader.readIntLeAt(0x08)
    val offsetAlignment = reader.readShortLeAt(0x0C).toInt() and 0xFFFF

    val usableHeader = offsetSize in AWB_OFFSET_SIZES &&
        waveIdAlignment in 1..MAX_TABLE_ALIGNMENT &&
        offsetAlignment in 1..MAX_TABLE_ALIGNMENT &&
        declaredCount in 1..MAX_CUES
    val idTableEnd = AWB_HEADER_SIZE.toLong() + declaredCount.toLong() * waveIdAlignment
    val offsetTableEnd = idTableEnd + (declaredCount.toLong() + 1L) * offsetSize
    if (!usableHeader || offsetTableEnd > size) return null

    return AwbHeader(
        declaredCount = declaredCount,
        entries = readEntries(reader, declaredCount, offsetSize, waveIdAlignment, offsetAlignment),
    )
}

/**
 * Reads the id table (a `u16le` wave id per cue, padded to [waveIdAlignment] bytes) and the offset
 * table (a start offset per cue plus the bank end), applying upstream's alignment rule: a start is
 * always rounded up, an end only while it stays inside the bank.
 */
private fun readEntries(
    reader: ByteReader,
    declaredCount: Int,
    offsetSize: Int,
    waveIdAlignment: Int,
    offsetAlignment: Int,
): List<AwbEntry> {
    val idTableEnd = AWB_HEADER_SIZE + declaredCount * waveIdAlignment
    return (0 until declaredCount).mapNotNull { index ->
        val waveId = reader.readShortLeAt(AWB_HEADER_SIZE + index * waveIdAlignment).toInt() and 0xFFFF
        val entryOffset = idTableEnd + index * offsetSize
        val rawStart = reader.tableOffset(entryOffset, offsetSize)
        val rawEnd = reader.tableOffset(entryOffset + offsetSize, offsetSize)
        val start = alignUp(rawStart, offsetAlignment)
        val end = alignEnd(rawEnd, offsetAlignment, reader.size)
        if (start < 0 || start >= reader.size || end <= start || end > reader.size) {
            null
        } else {
            AwbEntry(index, waveId, start, end)
        }
    }
}

/** One offset-table entry: `u32le` for the common width, `u16le` for the small-bank variant. */
private fun ByteReader.tableOffset(offset: Int, offsetSize: Int): Int =
    if (offsetSize == AWB_OFFSET_32) readIntLeAt(offset) else readShortLeAt(offset).toInt() and 0xFFFF

private fun alignUp(value: Int, alignment: Int): Int {
    val remainder = value % alignment
    return if (remainder == 0) value else value + (alignment - remainder)
}

private fun alignEnd(value: Int, alignment: Int, bankSize: Int): Int {
    val remainder = value % alignment
    return if (remainder != 0 && value < bankSize) value + (alignment - remainder) else value
}

/**
 * One cue's length, sniffed the way `load_meta_type` does: ADX (`u16be == 0x8000`) and HCA (masked
 * `HCA\0`, because CRI may set the magic's high bits) are supported; everything else is skipped.
 */
private fun readAwbWave(bytes: ByteArray, base: Int, entry: AwbEntry): AwbWave? {
    val offset = base + entry.offset
    val waveSize = entry.end - entry.offset
    // Mirror upstream's subfile streamfile: a codec header may not claim more bytes than the cue
    // actually owns, so a stray match in a neighbouring cue can never become a length.
    val lengthMs = when {
        bytes.u16beAt(offset) == ADX_SIGNATURE ->
            readAdxHeader(bytes, offset)?.takeIf { it.dataStart <= waveSize }?.lengthMs

        bytes.maskedU32beAt(offset) == HCA_MAGIC ->
            readHcaHeader(bytes, offset)?.takeIf { it.headerSize <= waveSize }?.lengthMs

        else -> null
    } ?: return null
    return AwbWave(entry.index, entry.waveId, lengthMs)
}

private fun ByteArray.u16beAt(index: Int): Int? {
    if (index < 0 || index + 2 > size) return null
    return ((this[index].toInt() and 0xFF) shl 8) or (this[index + 1].toInt() and 0xFF)
}

private fun ByteArray.maskedU32beAt(index: Int): Long? {
    if (index < 0 || index + 4 > size) return null
    var value = 0L
    for (i in 0 until 4) value = (value shl 8) or (this[index + i].toLong() and 0xFF)
    return value and HCA_MAGIC_MASK
}

internal const val EXT_AWB = "awb"
internal const val EXT_AFS2 = "afs2"
internal const val EXT_ACB = "acb"

private const val AFS2_MAGIC = "AFS2"
private const val AWB_HEADER_SIZE = 0x10
private const val AWB_OFFSET_32 = 4
private val AWB_OFFSET_SIZES = setOf(2, AWB_OFFSET_32)
private const val MAX_TABLE_ALIGNMENT = 0x100
private const val MAX_CUES = 0x10000
private const val ADX_SIGNATURE = 0x8000
private const val HCA_MAGIC = 0x0048434100L // "HCA\0" masked to 0x7F per byte
private const val HCA_MAGIC_MASK = 0x7F7F7F7FL
