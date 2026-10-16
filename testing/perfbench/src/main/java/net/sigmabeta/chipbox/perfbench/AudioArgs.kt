package net.sigmabeta.chipbox.perfbench

import java.io.File

internal const val DEFAULT_OUT = "build/perfbench"
internal const val DEFAULT_REPEAT = 3
internal const val DEFAULT_SAMPLE_SECONDS = 30

/** Parsed `perfbench audio` invocation. */
data class AudioArgs(
    val corpusDir: File,
    val filter: String?,
    val repeat: Int,
    val outDir: File,
    val sampleSeconds: Int,
)

/**
 * Turns parsed `--key value` options into an [AudioArgs], applying defaults and rejecting missing or
 * invalid input up front (before any native library is loaded). A `--filter` glob is validated here
 * too, so a malformed pattern fails at parse time rather than mid-walk.
 */
fun parseAudioOptions(options: Map<String, String>): AudioArgs {
    val corpusDir = parseCorpusDir(options)

    val repeat = intOption(options, "repeat") ?: DEFAULT_REPEAT
    require(repeat >= 1) { "--repeat must be >= 1 (was $repeat)" }

    val sampleSeconds = intOption(options, "seconds") ?: DEFAULT_SAMPLE_SECONDS
    require(sampleSeconds >= 1) { "--seconds must be >= 1 (was $sampleSeconds)" }

    val filter = options["filter"]
    filter?.let(::requireValidGlob)

    return AudioArgs(
        corpusDir = corpusDir,
        filter = filter,
        repeat = repeat,
        outDir = File(options["out"] ?: DEFAULT_OUT),
        sampleSeconds = sampleSeconds,
    )
}

/** Reads an integer option, rejecting a present-but-unparseable value instead of silently defaulting. */
private fun intOption(options: Map<String, String>, key: String): Int? {
    val value = options[key] ?: return null
    return value.toIntOrNull() ?: error("--$key must be an integer (was '$value')")
}
