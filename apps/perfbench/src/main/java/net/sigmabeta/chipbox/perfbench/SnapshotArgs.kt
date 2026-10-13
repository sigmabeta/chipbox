package net.sigmabeta.chipbox.perfbench

import java.io.File

internal const val DEFAULT_SNAPSHOT_OUT = "build/perfbench/library-snapshot.json"

/** Parsed `perfbench snapshot` invocation. */
data class SnapshotArgs(
    val corpusDir: File,
    val outFile: File,
)

/**
 * Turns parsed `--key value` options into a [SnapshotArgs]. `--dir` is required and must exist;
 * `--out` names the snapshot file to write (defaults to `build/perfbench/library-snapshot.json`).
 */
fun parseSnapshotOptions(options: Map<String, String>): SnapshotArgs {
    val dirValue = options["dir"]
        ?: error("snapshot requires --dir <corpus> (a folder of audio files)")
    val corpusDir = File(dirValue)
    require(corpusDir.isDirectory) { "Corpus directory not found: ${corpusDir.absolutePath}" }

    return SnapshotArgs(
        corpusDir = corpusDir,
        outFile = File(options["out"] ?: DEFAULT_SNAPSHOT_OUT),
    )
}
