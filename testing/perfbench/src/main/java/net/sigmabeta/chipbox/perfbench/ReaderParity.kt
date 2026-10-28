package net.sigmabeta.chipbox.perfbench

/** One row of a database/probe reference TSV: `id, path, trackNumber, lengthMs, extension, title`. */
internal data class ParityReferenceRow(
    val path: String,
    val trackNumber: Int,
    val storedLengthMs: Long,
    val extension: String,
)

/** One measured file/subsong. [expectedLengthMs] is null in directory-only mode (no reference). */
internal data class ParityCase(
    val path: String,
    val extension: String,
    val trackNumber: Int,
    val expectedLengthMs: Long?,
    val readerLengthMs: Long?,
    val note: String? = null,
)

internal enum class ParityResult { EXACT, MISMATCH, PARSED, UNPARSED }

internal data class ParityCounts(
    val exact: Int = 0,
    val mismatch: Int = 0,
    val parsed: Int = 0,
    val unparsed: Int = 0,
) {
    val rows: Int get() = exact + mismatch + parsed + unparsed

    operator fun plus(other: ParityCounts): ParityCounts = ParityCounts(
        exact = exact + other.exact,
        mismatch = mismatch + other.mismatch,
        parsed = parsed + other.parsed,
        unparsed = unparsed + other.unparsed,
    )
}

internal data class ParityProblem(
    val result: ParityResult,
    val extension: String,
    val path: String,
    val trackNumber: Int,
    val detail: String,
)

internal data class ParitySummary(
    val totals: ParityCounts,
    val perExtension: Map<String, ParityCounts>,
    val problems: List<ParityProblem>,
)

// id, path, trackNumber, lengthMs, extension, title — the title may itself contain tabs, so cap the
// split at the title column.
private const val REFERENCE_COLUMN_COUNT = 6
private const val REFERENCE_MIN_COLUMNS = 5
private const val COL_PATH = 1
private const val COL_TRACK_NUMBER = 2
private const val COL_LENGTH_MS = 3
private const val COL_EXTENSION = 4

/** Parses the headerless, tab-separated DB export (blank lines are ignored). */
internal fun parseReferenceTsv(text: String): List<ParityReferenceRow> {
    val rows = mutableListOf<ParityReferenceRow>()
    text.lineSequence().forEachIndexed { index, line ->
        if (line.isNotBlank()) rows += parseReferenceLine(line, index + 1)
    }
    return rows
}

private fun parseReferenceLine(line: String, lineNumber: Int): ParityReferenceRow {
    val columns = line.split('\t', limit = REFERENCE_COLUMN_COUNT)
    require(columns.size >= REFERENCE_MIN_COLUMNS) {
        "reference line $lineNumber: expected at least $REFERENCE_MIN_COLUMNS " +
            "tab-separated columns, got ${columns.size}"
    }
    val trackNumber = columns[COL_TRACK_NUMBER].trim().toIntOrNull()
        ?: throw IllegalArgumentException(
            "reference line $lineNumber: bad track number '${columns[COL_TRACK_NUMBER]}'",
        )
    val storedLengthMs = columns[COL_LENGTH_MS].trim().toLongOrNull()
        ?: throw IllegalArgumentException(
            "reference line $lineNumber: bad length '${columns[COL_LENGTH_MS]}'",
        )
    return ParityReferenceRow(
        path = columns[COL_PATH],
        trackNumber = trackNumber,
        storedLengthMs = storedLengthMs,
        extension = columns[COL_EXTENSION].trim().lowercase(),
    )
}

/**
 * Classifies every case and folds it into totals, per-extension totals, and the offending rows.
 * A case with an expected length is exact when `readerLengthMs + paddingMs == expectedLengthMs`
 * (the scanner pads zero-fade lengths by the same amount); without a reference a successfully read
 * length is [ParityResult.PARSED].
 */
internal fun summarizeParity(cases: List<ParityCase>, paddingMs: Long): ParitySummary {
    var totals = ParityCounts()
    val perExtension = mutableMapOf<String, ParityCounts>()
    val problems = mutableListOf<ParityProblem>()

    for (case in cases) {
        val result = classify(case, paddingMs)
        totals += countsFor(result)
        perExtension[case.extension] = (perExtension[case.extension] ?: ParityCounts()) + countsFor(result)
        if (result == ParityResult.MISMATCH || result == ParityResult.UNPARSED) {
            problems += problemFor(case, result, paddingMs)
        }
    }

    return ParitySummary(
        totals = totals,
        perExtension = perExtension.toSortedMap(),
        problems = problems,
    )
}

private fun classify(case: ParityCase, paddingMs: Long): ParityResult = when {
    case.readerLengthMs == null -> ParityResult.UNPARSED
    case.expectedLengthMs == null -> ParityResult.PARSED
    case.readerLengthMs + paddingMs == case.expectedLengthMs -> ParityResult.EXACT
    else -> ParityResult.MISMATCH
}

private fun countsFor(result: ParityResult): ParityCounts = when (result) {
    ParityResult.EXACT -> ParityCounts(exact = 1)
    ParityResult.MISMATCH -> ParityCounts(mismatch = 1)
    ParityResult.PARSED -> ParityCounts(parsed = 1)
    ParityResult.UNPARSED -> ParityCounts(unparsed = 1)
}

private fun problemFor(case: ParityCase, result: ParityResult, paddingMs: Long): ParityProblem {
    val detail = when (result) {
        ParityResult.MISMATCH ->
            "expected=${case.expectedLengthMs}ms actual=${case.readerLengthMs}+${paddingMs}ms"

        ParityResult.UNPARSED -> case.note ?: "reader produced no length"

        ParityResult.EXACT, ParityResult.PARSED -> ""
    }
    return ParityProblem(result, case.extension, case.path, case.trackNumber, detail)
}
