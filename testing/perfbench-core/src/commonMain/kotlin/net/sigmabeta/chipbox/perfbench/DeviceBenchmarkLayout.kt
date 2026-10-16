package net.sigmabeta.chipbox.perfbench

/**
 * The fixed on-device layout a device benchmark seeds from, shared so the app and the macrobenchmark
 * agree without either reaching into the other.
 *
 * Everything lives under **shared** external storage (e.g. `/sdcard/Music/chipbox-test/`), beside
 * where a user's real library would sit — _not_ under the app's external files dir
 * (`Android/data/<package>/`), which the macrobenchmark's compilation-mode reset deletes when it
 * uninstalls + reinstalls the app on API < 34. The host writes a library snapshot with
 * `perfbench snapshot`, pushes it here, and pushes the corpus audio to the sibling [CORPUS_DIR_NAME];
 * the snapshot's corpus-relative paths rebase onto that directory. Reading it needs All Files Access
 * (`MANAGE_EXTERNAL_STORAGE`), the same permission the app's folder picker already requires.
 */
object DeviceBenchmarkLayout {
    /** Directory under shared external storage holding the snapshot and corpus, e.g. `Music/chipbox-test`. */
    const val ROOT_DIR = "Music/chipbox-test"

    /** The library snapshot the app ingests at startup, relative to [ROOT_DIR]. */
    const val SNAPSHOT_FILE_NAME = "library-snapshot.json"

    /** The corpus audio the snapshot's relative paths rebase onto, relative to [ROOT_DIR]. */
    const val CORPUS_DIR_NAME = "corpus"
}
