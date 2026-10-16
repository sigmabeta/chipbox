package net.sigmabeta.chipbox.abrender

import java.io.File

/**
 * Bit-identity golden checks for the native emulator cores: render a supplied corpus, and compare
 * the result, sample-for-sample, against a previously recorded reference run.
 *
 * Unlike the screenshot goldens, these are **not** committed — the audio (and the corpus it is
 * rendered from) may not be redistributable. Both are supplied by whoever runs the check, so the
 * reference lives in a local, git-ignored directory. Because PCM must not depend on the CPU
 * architecture, one reference is compared against both the host JVM (x86_64) and a real device
 * (arm64); any difference — in either direction — is a failure.
 *
 * The comparison itself is [diffRuns], the same code `abrender diff` uses, so "bit-identical" means
 * exactly one thing everywhere.
 */
data class GoldenReport(val rows: List<DiffRow>) {
    /** Tracks that were not bit-identical — the reason a verify fails. */
    val failures: List<DiffRow> = rows.filter { it.verdict != Verdict.IDENTICAL }

    val passed: Boolean get() = failures.isEmpty()

    /** A human-readable failure summary, or a one-line pass note. */
    fun describe(): String = when {
        rows.isEmpty() -> "golden check compared 0 tracks — the corpus/walk matched nothing."

        passed -> "all ${rows.size} track(s) bit-identical to the golden."

        else -> {
            val detail = failures.joinToString("\n") { row ->
                val why = when (row.verdict) {
                    Verdict.CHANGED -> "CHANGED (diffRMS=${dbText(row.diffRmsDbfs)}, ${row.note})"
                    Verdict.ONLY_IN_A -> "MISSING from this run (golden has it)"
                    Verdict.ONLY_IN_B -> "EXTRA track (not in golden)"
                    else -> "${row.verdict}${if (row.note.isEmpty()) "" else " (${row.note})"}"
                }
                "  ! ${row.game} — ${row.title}  [$why]"
            }
            "${failures.size} of ${rows.size} track(s) diverged from the golden:\n$detail"
        }
    }

    private fun dbText(v: Double): String = when {
        v.isNaN() -> "NaN"
        v == Double.NEGATIVE_INFINITY -> "-inf"
        else -> v.fixed(decimals = 2)
    }
}

/** Filters shared by record and verify — everything that determines which tracks get rendered. */
data class GoldenSelection(
    val seconds: Int = GOLDEN_DEFAULT_SECONDS,
    val extensions: Set<String>? = null,
    val gameFilter: String? = null,
    val everySong: Boolean = false,
    val subsong: Int = 0,
    val limit: Int? = null,
    val maxWallMillis: Long = GOLDEN_DEFAULT_MAX_WALL_MILLIS,
)

const val GOLDEN_DEFAULT_SECONDS = 30
const val GOLDEN_DEFAULT_MAX_WALL_MILLIS = 120_000L

/** Conventional name of the golden run directory under a goldens root (`<root>/golden/`). */
const val GOLDEN_LABEL = "golden"

/**
 * Renders [corpusDir] into the reference run at [goldenDir], replacing any previous golden set. This
 * is the "record" half: the resulting dir is a normal abrender run (`metrics.tsv` + `wav/`), so it
 * can be listened to, diffed against a later run, or copied to a device to verify.
 */
fun recordGoldenRun(
    corpusDir: File,
    goldenDir: File,
    selection: GoldenSelection,
): RenderSummary {
    goldenDir.deleteRecursively() // a record replaces the set — no stale tracks from a wider corpus
    return runRender(renderArgs(corpusDir, goldenDir, selection, overwrite = true))
}

/**
 * Renders [corpusDir] fresh into [workDir] and compares every track against the reference run at
 * [goldenDir]. The caller decides what to do with [GoldenReport.passed] — the host/device tests fail
 * on it. [workDir] keeps the candidate WAVs for listening when something diverges.
 */
fun verifyGoldenRun(
    corpusDir: File,
    goldenDir: File,
    workDir: File,
    selection: GoldenSelection,
): GoldenReport {
    workDir.deleteRecursively()
    runRender(renderArgs(corpusDir, workDir, selection, overwrite = true))
    return compareGolden(goldenDir, workDir)
}

/** Compares two run dirs; every track must be [Verdict.IDENTICAL] for [GoldenReport.passed]. */
fun compareGolden(goldenDir: File, candidateDir: File): GoldenReport = GoldenReport(
    diffRuns(
        metricsA = readMetrics(File(goldenDir, "metrics.tsv")),
        metricsB = readMetrics(File(candidateDir, "metrics.tsv")),
        wavA = File(goldenDir, "wav"),
        wavB = File(candidateDir, "wav"),
    ),
)

/**
 * Builds the [Args] one `render` pass needs. [runDir] is the run directory itself; its parent is
 * `outDir` and its name the label, matching `runRender`'s `<out>/<label>` layout. Sharding is unused
 * here (single-process golden runs).
 */
private fun renderArgs(
    corpusDir: File,
    runDir: File,
    selection: GoldenSelection,
    overwrite: Boolean,
): Args = Args(
    command = "render",
    label = runDir.name,
    seconds = selection.seconds,
    outDir = checkNotNull(runDir.parentFile) { "golden run dir has no parent: $runDir" },
    corpusDir = corpusDir,
    extensions = selection.extensions,
    gameFilter = selection.gameFilter,
    everySong = selection.everySong,
    subsong = selection.subsong,
    limit = selection.limit,
    overwrite = overwrite,
    maxWallMillis = selection.maxWallMillis,
    shards = 1,
    shard = 0,
    runA = null,
    runB = null,
    topN = 0,
)
