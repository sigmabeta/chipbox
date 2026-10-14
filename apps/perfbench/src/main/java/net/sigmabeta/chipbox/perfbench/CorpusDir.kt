package net.sigmabeta.chipbox.perfbench

import java.io.File

/**
 * Resolves the required `--dir <corpus>` option every subcommand shares: rejects a missing value
 * and a path that is not an existing directory, so `audio` and `snapshot` fail identically on a
 * bad corpus.
 */
internal fun parseCorpusDir(options: Map<String, String>): File {
    val dirValue = options["dir"]
    require(dirValue != null) { "--dir <corpus> is required (a folder of audio files)" }
    val corpusDir = File(dirValue)
    require(corpusDir.isDirectory) { "Corpus directory not found: ${corpusDir.absolutePath}" }
    return corpusDir
}
