package net.sigmabeta.chipbox.perfbench

import java.io.File

/** Parsed `perfbench baseline` invocation: the recordings to merge, oldest first, and the output. */
data class BaselineArgs(
    val recordings: List<File>,
    val outFile: File,
)

/**
 * Turns parsed `--key value` options into a [BaselineArgs]. `--runs` is a comma-separated list of
 * result files, oldest first, and each must exist. The newest one supplies the merged document's
 * fingerprint. `--out` is the baseline file to write.
 */
fun parseBaselineOptions(options: Map<String, String>): BaselineArgs {
    val runs = options["runs"] ?: error("baseline requires --runs <a.json,b.json,...> (oldest first)")
    val out = options["out"] ?: error("baseline requires --out <baseline.json>")
    val recordings = runs.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::File)
    require(recordings.isNotEmpty()) { "baseline --runs names no files" }
    recordings.forEach { require(it.isFile) { "No recording at ${it.absolutePath}" } }
    return BaselineArgs(recordings = recordings, outFile = File(out))
}
