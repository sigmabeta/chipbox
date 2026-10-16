package net.sigmabeta.chipbox.perfbench

import java.io.File

/**
 * Parsed `perfbench diff` invocation. [baseline] is run A; [current] is run B. When [json] is set the
 * report is written as machine-readable JSON instead of the human table, so a caller (e.g. a
 * regression gate) can read the ratios rather than parse prose.
 */
data class DiffArgs(
    val baseline: File,
    val current: File,
    val json: Boolean,
)

/**
 * Turns parsed `--key value` options into a [DiffArgs]. Each of `--a`/`--b` is either a
 * `results.json` or a folder containing one; both are resolved and must exist up front.
 */
fun parseDiffOptions(options: Map<String, String>): DiffArgs {
    val a = options["a"] ?: error("diff requires --a <run> (a results.json or a folder containing one)")
    val b = options["b"] ?: error("diff requires --b <run> (a results.json or a folder containing one)")
    return DiffArgs(
        baseline = resolveResultsFile(a, "a"),
        current = resolveResultsFile(b, "b"),
        json = options["json"] == "true",
    )
}

private fun resolveResultsFile(value: String, flag: String): File {
    val candidate = File(value)
    val file = if (candidate.isDirectory) File(candidate, RESULTS_FILE_NAME) else candidate
    require(file.isFile) { "No $RESULTS_FILE_NAME for --$flag at ${file.absolutePath}" }
    return file
}
