package net.sigmabeta.chipbox.perfbench

import java.io.File

/** The scanner's zero-fade padding; stored streamed lengths are `reader length + this`. */
internal const val DEFAULT_PARITY_PADDING_MS = 2_000L

/**
 * Parsed `perfbench reader-parity` invocation.
 *
 * At least one of [corpusDir] / [referenceFile] is required. With a reference, its rows define the
 * work (optionally narrowed by [corpusDir] containment and [readerExtension]); without one, the
 * command walks [corpusDir] and runs every registered reader (or only [readerExtension]).
 */
data class ReaderParityArgs(
    val corpusDir: File?,
    val referenceFile: File?,
    val readerExtension: String?,
    val paddingMs: Long,
)

/**
 * Turns parsed `--key value` options into [ReaderParityArgs].
 *
 * `--reference` is a tab-separated database export (no header; see the README for the sqlite
 * capture command). `--reader` limits the run to one lowercase extension (a leading dot is
 * tolerated). `--no-padding` compares against raw probe lengths instead of the scanner's stored
 * lengths.
 */
fun parseReaderParityOptions(options: Map<String, String>): ReaderParityArgs {
    val corpusDir = options["dir"]?.let { path ->
        File(path).also { require(it.isDirectory) { "--dir must be an existing directory: $path" } }
    }
    val referenceFile = options["reference"]?.let { path ->
        File(path).also { require(it.isFile) { "--reference must be an existing file: $path" } }
    }
    require(corpusDir != null || referenceFile != null) {
        "reader-parity needs --dir and/or --reference"
    }

    return ReaderParityArgs(
        corpusDir = corpusDir,
        referenceFile = referenceFile,
        readerExtension = options["reader"]
            ?.trim()
            ?.removePrefix(".")
            ?.lowercase()
            ?.takeUnless { it.isEmpty() },
        paddingMs = if (options.containsKey("no-padding")) 0L else DEFAULT_PARITY_PADDING_MS,
    )
}
