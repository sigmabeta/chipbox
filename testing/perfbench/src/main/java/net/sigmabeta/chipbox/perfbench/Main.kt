package net.sigmabeta.chipbox.perfbench

/**
 * `perfbench` — the host-side performance benchmarking CLI. One entry point, one subcommand per
 * axis; the device axes live in the Android `:benchmark` module instead. v1 ships `audio`,
 * `snapshot`, and `diff`.
 */
fun main(rawArgs: Array<String>) {
    val command = rawArgs.firstOrNull()
    if (command == null || command in setOf("-h", "--help", "help")) {
        printUsage()
        return
    }
    val options = parseOptions(rawArgs.drop(1))

    when (command) {
        "audio" -> runAudio(parseAudioOptions(options))

        "snapshot" -> runSnapshot(parseSnapshotOptions(options))

        "diff" -> runDiff(parseDiffOptions(options))

        "reader-parity" -> runReaderParity(parseReaderParityOptions(options))

        else -> {
            println("Unknown command: $command\n")
            printUsage()
        }
    }
}

/**
 * Parses `--key value` and bare `--flag` options. A token starting with `--` followed by another
 * `--` token (or the end of input) is a boolean flag; otherwise it consumes the next token.
 */
private fun parseOptions(tokens: List<String>): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    var i = 0
    while (i < tokens.size) {
        val token = tokens[i]
        require(token.startsWith("--")) { "Unexpected argument '$token' (options must start with --)" }
        val key = token.removePrefix("--")
        val next = tokens.getOrNull(i + 1)
        if (next == null || next.startsWith("--")) {
            out[key] = "true"
            i += 1
        } else {
            out[key] = next
            i += 2
        }
    }
    return out
}

private fun printUsage() {
    println(
        """
        perfbench — performance benchmarking harness for Chipbox.

        Usage:
          perfbench audio --dir <corpus> [options]
          perfbench snapshot --dir <corpus> [--out <snapshot>]
          perfbench diff --a <runA> --b <runB>
          perfbench reader-parity --dir <corpus> [--reference <tsv>] [--reader <ext>] [--no-padding]

        audio — measure real-time factor (RTF) over a corpus. Renders every supported corpus file
        through the raw emulator core, one result per file keyed by its corpus-relative path, and
        reports the median RTF of N timed repeats after one warm-up. A file that fails to render is
        recorded as an errored result rather than dropped. Writes <out>/results.json with the host
        fingerprint.

          --dir <folder>       corpus root to walk for audio files        (required)
          --filter <glob>      glob over each file's corpus-relative path (default: whole corpus)
                               no '/' -> basename at any depth (*.spc); with '/' -> full path
                               (Chrono Trigger/**), where ** crosses dirs and * does not
          --repeat <n>         timed repeats per file (median of N)       (default: $DEFAULT_REPEAT)
          --seconds <n>        audio seconds rendered per repeat          (default: $DEFAULT_SAMPLE_SECONDS)
          --out <dir>          results root; results.json is written here  (default: $DEFAULT_OUT)

        snapshot — run the real host scanner once over the corpus and write a portable library
        snapshot (raw scan output with corpus-relative paths) for the app to ingest without rescanning.

          --dir <folder>       corpus root to scan                        (required)
          --out <snapshot>     snapshot file to write                     (default: $DEFAULT_SNAPSHOT_OUT)

        diff — compare every axis of two runs. Run A is the baseline, run B the current run. Prints
        each scenario's ratio (its axis's baseline cost ÷ current cost) and the geometric mean, per
        axis and overall. Refuses to compare runs from a different fingerprint class, scenario set,
        or corpus manifest rather than report a silently wrong ratio.

          --a <run>            baseline results.json (or the folder holding it)   (required)
          --b <run>            current results.json (or the folder holding it)    (required)
          --json               emit the report as JSON instead of the human table (default: off)

        reader-parity — dev-only header-reader parity check (not a CI gate). Runs the registered
        reader for each reference row or corpus file and compares its length + padding against the
        stored length. With no reference it just reports parsed / unparsed files.

          --reference <tsv>    headerless DB export: id, path, trackNumber, length, extension, title
          --dir <folder>       corpus root; with --reference, only rows under it are run
          --reader <ext>       only files/rows with this extension (default: every reader)
          --no-padding         compare raw probe lengths instead of stored (padded) lengths
        """.trimIndent(),
    )
}
