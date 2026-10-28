package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * CRI ACB cue sheet (`.acb`) — the `@UTF` table that names the waves of a CRI Atom/ADX2 game bank.
 *
 * Ports the pieces of `cbox/native/vgmstream/Core/vgmstream/src/meta/acb.c` that can be answered
 * from a single file:
 * - the `Header` table's `AwbFile` column, which **embeds** the wave bank ([readAwbBank]);
 * - the cue-name → cue → waveform chain (`CueNameTable`, `CueTable`, `WaveformTable`) for titles,
 *   following only `ReferenceType == 1` (cue directly references a waveform). Names are attached to
 *   the bank's wave ids, and a wave keeps the cue name only when exactly one non-blank name maps to
 *   it **and** that name is unique across the file — otherwise it falls back to [TAG_UNKNOWN], per
 *   ticket 09.
 *
 * **Bytes-only boundary.** A `Reader` receives one file's bytes and cannot open a sibling `.awb`, so
 * an ACB whose `AwbFile` column is empty (the common external/streaming bank layout) yields
 * [emptyList] — recognised and skipped, never guessed at. Likewise the encryption boundary: ACB
 * headers are not keyed, but an ACB that wraps an encrypted CPK, or whose `@UTF` table is itself
 * unreadable, yields no tracks. HCA/ADX cue payloads may be encrypted (`ciph` type 56 / ADX
 * `0x0408`+); their headers stay readable without a key, so metadata is unaffected.
 *
 * The emitted track set is the bank's wave list — the same `subsong` model the native probe
 * enumerates — so `trackNumber` stays 1-based and aligned with the wave index.
 */
class AcbReader(private val hatchet: Hatchet) : Reader() {
    override val version: Int = ACB_VERSION

    override fun readTracksFromFile(bytes: ByteArray, identifier: String): List<RawTrack>? =
        if (!bytes.hasAsciiPrefix(UTF_MAGIC)) {
            hatchet.w("ACB parse failed: $identifier is not a CRI @UTF cue sheet.")
            null
        } else {
            readCueSheet(bytes, identifier)
        }

    private fun readCueSheet(bytes: ByteArray, identifier: String): List<RawTrack> {
        val bank = try {
            readAcbBank(bytes)
        } catch (e: IndexOutOfBoundsException) {
            // Defensive: the @UTF reader reports malformed tables as null; never fail a scan.
            null
        }
        if (bank == null || bank.waves.isEmpty()) {
            hatchet.w(
                "ACB skipped: $identifier has no readable embedded wave bank " +
                    "(external .awb, encrypted header or unsupported cue table).",
            )
            return emptyList()
        }
        val skipped = bank.declaredCount - bank.waves.size
        if (skipped > 0) {
            hatchet.w("ACB: $identifier skipped $skipped of ${bank.declaredCount} cue(s).")
        }
        return bank.toTracks(identifier)
    }

    companion object {
        const val ACB_VERSION = 1
    }
}

/**
 * A parsed ACB: the waves of its embedded bank plus the cue names keyed by AWB wave id. An empty
 * [waves] list means there was no readable embedded bank — the bytes-only "cannot resolve" case.
 */
internal data class AcbBank(
    val waves: List<AwbWave>,
    val declaredCount: Int,
    val names: Map<Int, Set<String>>,
)

/**
 * Parses an ACB from [bytes]. Returns null when the file is not an ACB-shaped `@UTF` table (wrong
 * table name, malformed schema). Anything recognised as an ACB but without a usable embedded bank
 * returns an [AcbBank] with no waves, so the reader can report "skipped" rather than "failed".
 */
internal fun readAcbBank(bytes: ByteArray): AcbBank? =
    CriUtfTable.read(bytes, 0)
        ?.takeIf { it.name == HEADER_TABLE && it.rows == 1 && it.tableSize == bytes.size }
        ?.let { header ->
            val awb = header.data(0, AWB_FILE_COLUMN)
            if (awb == null || awb.size == 0) {
                AcbBank(emptyList(), 0, emptyMap())
            } else {
                readAwbBank(bytes, awb.offset, awb.size)
                    ?.let { bank -> AcbBank(bank.waves, bank.declaredCount, readAcbNames(bytes, header)) }
                    ?: AcbBank(emptyList(), 0, emptyMap())
            }
        }

/** The tables a name lookup needs; all three must exist or names are simply unavailable. */
private class AcbNameTables(
    val cueNames: CriUtfTable,
    val cues: CriUtfTable,
    val waveforms: CriUtfTable,
)

private fun readAcbNames(bytes: ByteArray, header: CriUtfTable): Map<Int, Set<String>> =
    nameTables(bytes, header)?.let { collectWaveNames(it) } ?: emptyMap()

private fun nameTables(bytes: ByteArray, header: CriUtfTable): AcbNameTables? =
    header.subtable(bytes, CUE_NAME_TABLE)?.let { cueNames ->
        header.subtable(bytes, CUE_TABLE)?.let { cues ->
            header.subtable(bytes, WAVEFORM_TABLE)?.let { waveforms ->
                AcbNameTables(cueNames, cues, waveforms)
            }
        }
    }

/**
 * Resolves cue names to AWB wave ids through `CueNameTable.CueIndex` → `CueTable` (only
 * `ReferenceType == 1`) → `WaveformTable` row → its `Id` (or `MemoryAwbId` in older banks).
 * Streaming-only waveform rows are ignored for an embedded (memory) bank, as upstream does.
 */
private fun collectWaveNames(tables: AcbNameTables): Map<Int, Set<String>> {
    val waveIds = waveformIds(tables.waveforms)
    val cueIndexColumn = tables.cueNames.column("CueIndex")
    val cueNameColumn = tables.cueNames.column("CueName")
    val referenceTypeColumn = tables.cues.column("ReferenceType")
    val referenceIndexColumn = tables.cues.column("ReferenceIndex")
    val columnsPresent = listOf(cueIndexColumn, cueNameColumn, referenceTypeColumn, referenceIndexColumn)
        .all { it >= 0 }
    if (!columnsPresent) return emptyMap()

    val names = mutableMapOf<Int, MutableSet<String>>()
    for (row in 0 until tables.cueNames.rows) {
        val cueIndex = tables.cueNames.u16(row, cueIndexColumn) ?: continue
        val name = tables.cueNames.string(row, cueNameColumn) ?: continue
        val waveformIndex = waveformForCue(tables.cues, cueIndex, referenceTypeColumn, referenceIndexColumn)
            ?: continue
        val waveId = waveIds[waveformIndex] ?: continue
        if (name.isNotBlank()) names.getOrPut(waveId) { mutableSetOf() }.add(name)
    }
    return names
}

private fun waveformForCue(cues: CriUtfTable, cueIndex: Int, typeColumn: Int, indexColumn: Int): Int? =
    if (cues.u8(cueIndex, typeColumn) != REFERENCE_TYPE_WAVEFORM) null else cues.u16(cueIndex, indexColumn)

private fun waveformIds(waveforms: CriUtfTable): Map<Int, Int> {
    val idColumn = waveforms.column("Id").takeIf { it >= 0 }
        ?: waveforms.column("MemoryAwbId").takeIf { it >= 0 }
        ?: return emptyMap()
    val streamingColumn = waveforms.column("Streaming")
    return (0 until waveforms.rows).mapNotNull { row ->
        val waveId = waveforms.u16(row, idColumn) ?: return@mapNotNull null
        val streaming = if (streamingColumn >= 0) waveforms.u8(row, streamingColumn) else 0
        if (streaming == STREAMING_WAVE) null else row to waveId
    }.toMap()
}

/**
 * Builds one track per readable wave. A wave's title is its single unique cue name; duplicates
 * (the same name on several waves) and blank names fall back to [TAG_UNKNOWN], so the scanner's
 * naming rules never see an ambiguous title.
 */
private fun AcbBank.toTracks(identifier: String): List<RawTrack> {
    val candidates = waves.mapNotNull { wave ->
        names[wave.waveId]?.singleOrNull()?.let { name -> wave.index to name }
    }.toMap()
    val nameCounts = candidates.values.groupingBy { it }.eachCount()
    return waves.map { wave ->
        val title = candidates[wave.index]?.takeIf { nameCounts.getValue(it) == 1 } ?: TAG_UNKNOWN
        streamedRawTrack(identifier, wave.lengthMs, title, wave.index)
    }
}

private const val UTF_MAGIC = "@UTF"
private const val HEADER_TABLE = "Header"
private const val AWB_FILE_COLUMN = "AwbFile"
private const val CUE_NAME_TABLE = "CueNameTable"
private const val CUE_TABLE = "CueTable"
private const val WAVEFORM_TABLE = "WaveformTable"
private const val REFERENCE_TYPE_WAVEFORM = 1
private const val STREAMING_WAVE = 1
