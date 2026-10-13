package net.sigmabeta.chipbox.perfbench

/**
 * `perfbench` — the host-side performance benchmarking CLI. One entry point, one subcommand per
 * axis; the device axes live in the Android `:benchmark` module instead. v1 ships `audio`.
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

        audio — measure real-time factor (RTF) per emulator over a corpus. Renders each scenario's
        representative track through the raw emulator core and reports the median RTF of N timed
        repeats after one warm-up. Writes <out>/results.json with the host fingerprint.

          --dir <folder>       corpus root to walk for audio files        (required)
          --only <a,b>         only these scenario ids                    (default: all)
                               known ids: audio.usf, audio.vgm, audio.psf
          --repeat <n>         timed repeats per scenario (median of N)   (default: $DEFAULT_REPEAT)
          --seconds <n>        audio seconds rendered per repeat          (default: $DEFAULT_SAMPLE_SECONDS)
          --out <dir>          results root; results.json is written here  (default: $DEFAULT_OUT)
        """.trimIndent(),
    )
}
