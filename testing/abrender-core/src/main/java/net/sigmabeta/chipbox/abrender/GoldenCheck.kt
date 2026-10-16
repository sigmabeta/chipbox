package net.sigmabeta.chipbox.abrender

/**
 * The pass/fail semantics of a bit-identity comparison: render a corpus and compare it, sample for
 * sample, against a previously recorded **reference** run. [diffRuns] does the comparison (the same
 * code `abrender diff` uses); [GoldenTolerance] decides how close is close enough and [GoldenReport]
 * reduces the rows to a verdict the CLI's `diff --check`, and the `record.sh`/`verify.sh` scripts
 * built on it, act on.
 *
 * Reference PCM is **not** committed — the audio (and the corpus it is rendered from) may not be
 * redistributable — so references live in a local, git-ignored run store (`ab-runs/`).
 */
data class GoldenReport(
    val rows: List<DiffRow>,
    val tolerance: GoldenTolerance = GoldenTolerance.STRICT,
) {
    /** Tracks that differ but are within [tolerance] — reported, but not a failure. */
    val tolerated: List<DiffRow> = rows.filter { tolerance.tolerates(it) }

    /** Tracks that fail even under [tolerance] — the reason a verify fails. */
    val failures: List<DiffRow> = rows.filter { it.verdict != Verdict.IDENTICAL && !tolerance.tolerates(it) }

    val passed: Boolean get() = failures.isEmpty()

    /** A human-readable failure summary, or a one-line pass note. */
    fun describe(): String = when {
        rows.isEmpty() -> "compared 0 tracks — the corpus/walk matched nothing."

        passed && tolerated.isEmpty() -> "all ${rows.size} track(s) bit-identical to the reference."

        passed -> {
            val detail = tolerated.joinToString("\n") { row ->
                "  ~ ${row.game} — ${row.title}  [within tolerance (diffRMS=${dbText(row.diffRmsDbfs)}, ${row.note})]"
            }
            "all ${rows.size} track(s) within tolerance " +
                "(${tolerated.size} tolerated at maxΔ≤${tolerance.maxDelta}):\n$detail"
        }

        else -> {
            val detail = failures.joinToString("\n") { row ->
                val why = when (row.verdict) {
                    Verdict.CHANGED -> "CHANGED (diffRMS=${dbText(row.diffRmsDbfs)}, ${row.note})"
                    Verdict.ONLY_IN_A -> "MISSING from this run (reference has it)"
                    Verdict.ONLY_IN_B -> "EXTRA track (not in reference)"
                    else -> "${row.verdict}${if (row.note.isEmpty()) "" else " (${row.note})"}"
                }
                "  ! ${row.game} — ${row.title}  [$why]"
            }
            val toleratedNote =
                if (tolerated.isEmpty()) "" else " (${tolerated.size} other track(s) within tolerance)"
            "${failures.size} of ${rows.size} track(s) diverged from the reference$toleratedNote:\n$detail"
        }
    }

    private fun dbText(v: Double): String = when {
        v.isNaN() -> "NaN"
        v == Double.NEGATIVE_INFINITY -> "-inf"
        else -> v.fixed(decimals = 2)
    }
}

/**
 * How far a track may drift from the reference and still pass. Only [Verdict.CHANGED] tracks can be
 * tolerated: a missing/extra track or an error is never a rounding artifact.
 *
 * - [STRICT] is bit-identity — the default, and what a host-to-host comparison uses.
 * - [CROSS_ISA] allows a single LSB of difference, for comparing an arm64 render against an x86_64
 *   reference. Some cores resample through a `double` stage (the GBA/mGBA sinc resampler is the one
 *   in this tree), and `sin`/`cos` are not bit-identical between glibc and Bionic, so a few samples
 *   can land one quantisation step apart. `maxDelta ≤ 1` is the binding criterion; the RMS floor is
 *   a secondary sanity bound (any `maxΔ ≤ 1` diff is already ≤ −90 dBFS).
 */
data class GoldenTolerance(
    val maxDelta: Int,
    val maxDiffRmsDbfs: Double,
) {
    /** True if [row] differs from the reference but sits inside this tolerance. */
    fun tolerates(row: DiffRow): Boolean =
        row.verdict == Verdict.CHANGED && withinDelta(row) && withinFloor(row)

    private fun withinDelta(row: DiffRow): Boolean =
        row.maxDelta != null && row.maxDelta <= maxDelta

    private fun withinFloor(row: DiffRow): Boolean =
        !row.diffRmsDbfs.isNaN() && row.diffRmsDbfs <= maxDiffRmsDbfs

    companion object {
        /** Bit-identical: no tolerance at all. */
        val STRICT = GoldenTolerance(maxDelta = 0, maxDiffRmsDbfs = Double.NEGATIVE_INFINITY)

        /** arm64-vs-x86_64: one LSB, well below audibility. */
        val CROSS_ISA = GoldenTolerance(maxDelta = 1, maxDiffRmsDbfs = -80.0)
    }
}
