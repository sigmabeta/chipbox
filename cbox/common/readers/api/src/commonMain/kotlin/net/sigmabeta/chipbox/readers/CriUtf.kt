package net.sigmabeta.chipbox.readers

/**
 * Minimal reader for CRI's `@UTF` table format — the generic row/column database behind `.acb` cue
 * sheets (and `.acf`, `.cpk`, …). Ported from
 * `cbox/native/vgmstream/Core/vgmstream/src/util/cri_utf.c`; only the pieces the ACB reader needs
 * are exposed: named columns of the integer/string/vl-data types, addressed by row.
 *
 * The table stores its sections back to back: a 0x20-byte header, the schema (one
 * `flag|type` + column-name offset entry per column, plus any `DEFAULT` inline values), the row
 * section, the string table and the binary data section. Offsets in the header are stored minus 8.
 * All offsets here are absolute within [bytes] so a subtable embedded anywhere in an ACB resolves
 * the same way it does upstream.
 *
 * Every accessor returns null for an out-of-range row/column, a type mismatch or a bad offset; the
 * table class never throws on malformed input (construction reports failure with null).
 */
internal class CriUtfTable internal constructor(
    private val bytes: ByteArray,
    val name: String,
    val rows: Int,
    val tableSize: Int,
    private val rowsStart: Int,
    private val rowWidth: Int,
    private val stringsStart: Int,
    private val stringsSize: Int,
    private val dataStart: Int,
    private val dataSectionSize: Int,
    private val columns: List<Column>,
) {
    internal data class Column(
        val name: String,
        val type: Int,
        val rowOffset: Int,
        val defaultValuePosition: Int?,
    )

    /** Index of the column named [name], or -1 — upstream's `utf_get_column`. */
    fun column(name: String): Int = columns.indexOfFirst { it.name == name }

    fun u8(row: Int, column: Int): Int? = integer(row, column, TYPE_UINT8, TYPE_SINT8)?.toInt()

    fun u16(row: Int, column: Int): Int? = integer(row, column, TYPE_UINT16, TYPE_SINT16)?.toInt()

    fun string(row: Int, column: Int): String? =
        columnAt(row, column)
            ?.takeIf { it.type == TYPE_STRING }
            ?.let { valuePosition(row, it) }
            ?.let { readUnsignedBe(it, INT32_SIZE) }
            ?.let { readString(it.toInt()) }

    /** A `vlData` cell as (absolute offset, byte size) — upstream's `utf_query_data`. */
    fun data(row: Int, column: Int): CriUtfData? =
        columnAt(row, column)
            ?.takeIf { it.type == TYPE_VLDATA }
            ?.let { valuePosition(row, it) }
            ?.let { position ->
                val offset = readUnsignedBe(position, INT32_SIZE)?.toInt()
                val size = readUnsignedBe(position + INT32_SIZE, INT32_SIZE)?.toInt()
                if (offset != null && size != null && offset >= 0 && size >= 0 &&
                    offset.toLong() + size <= dataSectionSize
                ) {
                    CriUtfData(dataStart + offset, size)
                } else {
                    null
                }
            }

    fun data(row: Int, name: String): CriUtfData? = column(name).takeIf { it >= 0 }?.let { data(row, it) }

    /** One embedded `@UTF` table referenced by this table's named `vlData` cell. */
    fun subtable(bytes: ByteArray, name: String): CriUtfTable? =
        data(0, name)?.takeIf { it.size > 0 }?.let { CriUtfTable.read(bytes, it.offset) }

    private fun integer(row: Int, column: Int, unsignedType: Int, signedType: Int): Long? =
        columnAt(row, column)
            ?.takeIf { it.type == unsignedType || it.type == signedType }
            ?.let { valuePosition(row, it)?.let { position -> readUnsignedBe(position, typeSize(it.type)) } }

    private fun columnAt(row: Int, column: Int): Column? =
        columns.getOrNull(column)?.takeIf { row in 0 until rows }

    private fun valuePosition(row: Int, column: Column): Int? {
        // Upstream gives a DEFAULT column priority over a ROW column when both flags are set.
        val absolute = column.defaultValuePosition ?: (rowsStart + row * rowWidth + column.rowOffset)
        return absolute.takeIf { it >= 0 && it < bytes.size }
    }

    private fun readUnsignedBe(position: Int, size: Int): Long? {
        if (position < 0 || position + size > bytes.size) return null
        var value = 0L
        for (i in 0 until size) value = (value shl 8) or (bytes[position + i].toLong() and 0xFF)
        return value
    }

    private fun readString(nameOffset: Int): String? {
        if (nameOffset < 0 || nameOffset > stringsSize) return null
        val start = stringsStart + nameOffset
        val limit = stringsStart + stringsSize
        var end = start
        while (end < limit && bytes[end] != 0.toByte()) end++
        return bytes.decodeToString(start, end)
    }

    companion object {
        fun read(bytes: ByteArray, offset: Int): CriUtfTable? =
            try {
                parse(bytes, offset)
            } catch (e: CriUtfCorruptException) {
                null
            } catch (e: IndexOutOfBoundsException) {
                // Every field is pre-flighted; a miss is still preferable to a scan failure.
                null
            }
    }
}

/** One `vlData` cell: the absolute offset and size of its binary payload inside the container. */
internal data class CriUtfData(val offset: Int, val size: Int)

/** Structurally invalid table: aborts the parse, which [CriUtfTable.read] reports as null. */
private class CriUtfCorruptException(message: String) : Exception(message)

private fun parse(bytes: ByteArray, offset: Int): CriUtfTable {
    if (offset < 0 || bytes.size - offset < UTF_HEADER_SIZE) throw CriUtfCorruptException("truncated header")
    if (!bytes.hasAsciiAt(offset, UTF_MAGIC)) throw CriUtfCorruptException("missing @UTF magic")

    val tableSize = (bytes.u32beAt(offset + 0x04) ?: throw CriUtfCorruptException("truncated header")) + 8
    if (tableSize < UTF_HEADER_SIZE.toLong() || offset.toLong() + tableSize > bytes.size) {
        throw CriUtfCorruptException("table size $tableSize does not fit the file")
    }
    val version = bytes.u16beAt(offset + 0x08) ?: throw CriUtfCorruptException("truncated header")
    if (version != 0 && version != 1) throw CriUtfCorruptException("unsupported version $version")

    val rowsOffset = bytes.u16beAt(offset + 0x0A)?.toLong()?.plus(8)
        ?: throw CriUtfCorruptException("truncated header")
    val stringsOffset = bytes.u32beAt(offset + 0x0C)?.plus(8) ?: throw CriUtfCorruptException("truncated header")
    val dataOffset = bytes.u32beAt(offset + 0x10)?.plus(8) ?: throw CriUtfCorruptException("truncated header")
    val nameOffset = bytes.u32beAt(offset + 0x14) ?: throw CriUtfCorruptException("truncated header")
    val columnCount = bytes.u16beAt(offset + 0x18) ?: throw CriUtfCorruptException("truncated header")
    val rowWidth = bytes.u16beAt(offset + 0x1A) ?: throw CriUtfCorruptException("truncated header")
    val rowCount = bytes.u32beAt(offset + 0x1C) ?: throw CriUtfCorruptException("truncated header")

    val sectionsValid = rowsOffset in UTF_HEADER_SIZE.toLong()..tableSize &&
        stringsOffset in rowsOffset..tableSize &&
        dataOffset in stringsOffset..tableSize &&
        rowsOffset - UTF_HEADER_SIZE < MAX_SCHEMA_SIZE
    if (!sectionsValid) throw CriUtfCorruptException("invalid table sections")

    val stringsSize = (dataOffset - stringsOffset).toInt()
    if (stringsSize <= 0 || nameOffset > stringsSize.toLong()) throw CriUtfCorruptException("invalid string table")
    val rowBytes = rowCount * rowWidth
    if (rowCount !in 0..MAX_ROWS || rowWidth < 0 || rowBytes > stringsOffset - rowsOffset) {
        throw CriUtfCorruptException("row section does not fit")
    }

    val columns = parseColumns(
        bytes = bytes,
        tableOffset = offset,
        schemaEnd = rowsOffset.toInt(),
        stringsStart = offset + stringsOffset.toInt(),
        stringsSize = stringsSize,
        rowWidth = rowWidth,
        columnCount = columnCount,
    )

    val name = readCString(bytes, offset + stringsOffset.toInt(), nameOffset.toInt(), stringsSize)
        ?: throw CriUtfCorruptException("invalid table name")

    return CriUtfTable(
        bytes = bytes,
        name = name,
        rows = rowCount.toInt(),
        tableSize = tableSize.toInt(),
        rowsStart = offset + rowsOffset.toInt(),
        rowWidth = rowWidth,
        stringsStart = offset + stringsOffset.toInt(),
        stringsSize = stringsSize,
        dataStart = offset + dataOffset.toInt(),
        dataSectionSize = (tableSize - dataOffset).toInt(),
        columns = columns,
    )
}

/**
 * Walks the schema, resolving each column's name and value slot. `DEFAULT` columns store their
 * value inline in the schema section; `ROW` columns get a running offset into each row.
 */
private fun parseColumns(
    bytes: ByteArray,
    tableOffset: Int,
    schemaEnd: Int,
    stringsStart: Int,
    stringsSize: Int,
    rowWidth: Int,
    columnCount: Int,
): List<CriUtfTable.Column> {
    if (columnCount <= 0) throw CriUtfCorruptException("no columns")
    var schemaPosition = UTF_HEADER_SIZE
    var rowOffset = 0
    val columns = (0 until columnCount).map {
        val info = bytes.u8beAt(tableOffset + schemaPosition) ?: throw CriUtfCorruptException("truncated schema")
        val nameOffset = bytes.u32beAt(tableOffset + schemaPosition + 1)
            ?: throw CriUtfCorruptException("truncated schema")
        schemaPosition += SCHEMA_COLUMN_SIZE

        val flag = info and COLUMN_FLAG_MASK
        val type = info and COLUMN_TYPE_MASK
        if (flag and COLUMN_FLAG_NAME == 0 || flag and COLUMN_FLAG_UNDEFINED != 0) {
            throw CriUtfCorruptException("unknown column flags 0x${flag.toString(16)}")
        }
        val size = typeSize(type)
        if (nameOffset > stringsSize) throw CriUtfCorruptException("invalid column name offset")
        val name = readCString(bytes, stringsStart, nameOffset.toInt(), stringsSize)
            ?: throw CriUtfCorruptException("invalid column name")

        when {
            flag and COLUMN_FLAG_DEFAULT != 0 -> {
                val position = tableOffset + schemaPosition
                schemaPosition += size
                CriUtfTable.Column(name, type, rowOffset = -1, defaultValuePosition = position)
            }

            flag and COLUMN_FLAG_ROW != 0 -> {
                val slot = rowOffset
                rowOffset += size
                CriUtfTable.Column(name, type, rowOffset = slot, defaultValuePosition = null)
            }

            else -> throw CriUtfCorruptException("column has neither a default nor a row value")
        }
    }
    if (schemaPosition > schemaEnd || rowOffset > rowWidth) {
        throw CriUtfCorruptException("schema overruns its section")
    }
    return columns
}

private fun readCString(bytes: ByteArray, start: Int, nameOffset: Int, stringsSize: Int): String? {
    if (nameOffset < 0 || nameOffset > stringsSize) return null
    val from = start + nameOffset
    val limit = start + stringsSize
    var end = from
    while (end < limit && bytes[end] != 0.toByte()) end++
    return bytes.decodeToString(from, end)
}

private fun typeSize(type: Int): Int = when (type) {
    TYPE_UINT8, TYPE_SINT8 -> 1
    TYPE_UINT16, TYPE_SINT16 -> 2
    TYPE_UINT32, TYPE_SINT32, TYPE_FLOAT, TYPE_STRING -> INT32_SIZE
    TYPE_UINT64, TYPE_SINT64, TYPE_VLDATA -> 8
    else -> throw CriUtfCorruptException("unsupported column type 0x${type.toString(16)}")
}

private fun ByteArray.u8beAt(index: Int): Int? =
    if (index < 0 || index + 1 > size) null else this[index].toInt() and 0xFF

private fun ByteArray.u16beAt(index: Int): Int? {
    if (index < 0 || index + 2 > size) return null
    return ((this[index].toInt() and 0xFF) shl 8) or (this[index + 1].toInt() and 0xFF)
}

private fun ByteArray.u32beAt(index: Int): Long? {
    if (index < 0 || index + 4 > size) return null
    var value = 0L
    for (i in 0 until 4) value = (value shl 8) or (this[index + i].toLong() and 0xFF)
    return value
}

private const val UTF_MAGIC = "@UTF"
private const val UTF_HEADER_SIZE = 0x20
private const val MAX_SCHEMA_SIZE = 0x8000L
private const val MAX_ROWS = 0x100000L
private const val SCHEMA_COLUMN_SIZE = 5
private const val INT32_SIZE = 4
private const val COLUMN_FLAG_MASK = 0xF0
private const val COLUMN_TYPE_MASK = 0x0F
private const val COLUMN_FLAG_NAME = 0x10
private const val COLUMN_FLAG_DEFAULT = 0x20
private const val COLUMN_FLAG_ROW = 0x40
private const val COLUMN_FLAG_UNDEFINED = 0x80
private const val TYPE_UINT8 = 0x00
private const val TYPE_SINT8 = 0x01
private const val TYPE_UINT16 = 0x02
private const val TYPE_SINT16 = 0x03
private const val TYPE_UINT32 = 0x04
private const val TYPE_SINT32 = 0x05
private const val TYPE_UINT64 = 0x06
private const val TYPE_SINT64 = 0x07
private const val TYPE_FLOAT = 0x08
private const val TYPE_STRING = 0x0A
private const val TYPE_VLDATA = 0x0B
