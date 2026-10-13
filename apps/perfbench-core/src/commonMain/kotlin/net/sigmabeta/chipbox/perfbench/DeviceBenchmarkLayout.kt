package net.sigmabeta.chipbox.perfbench

/**
 * The fixed on-device layout a device benchmark seeds from, shared so the app and the macrobenchmark
 * agree without either reaching into the other.
 *
 * Everything lives under the app's external files dir (`Android/data/<package>/files/perfbench/`),
 * which `adb push` can write and the app can read with no runtime storage permission: the host
 * writes a library snapshot with `perfbench snapshot`, pushes it here, and pushes the corpus audio
 * to the sibling [CORPUS_DIR_NAME]; the snapshot's corpus-relative paths rebase onto that directory.
 */
object DeviceBenchmarkLayout {
    /** The directory, under the app's external files dir, holding the snapshot and corpus. */
    const val ROOT_DIR = "perfbench"

    /** The library snapshot the app ingests at startup, relative to [ROOT_DIR]. */
    const val SNAPSHOT_FILE_NAME = "library-snapshot.json"

    /** The corpus audio the snapshot's relative paths rebase onto, relative to [ROOT_DIR]. */
    const val CORPUS_DIR_NAME = "corpus"
}
