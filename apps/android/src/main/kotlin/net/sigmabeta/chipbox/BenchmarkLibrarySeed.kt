package net.sigmabeta.chipbox

import android.content.Context
import android.os.Environment
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import net.sigmabeta.chipbox.perfbench.DeviceBenchmarkLayout
import net.sigmabeta.chipbox.perfbench.sha256Hex
import net.sigmabeta.chipbox.repository.LibrarySnapshot
import net.sigmabeta.chipbox.repository.LibrarySnapshotJson
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.importLibrarySnapshot
import net.sigmabeta.sage.logging.Hatchet

/**
 * Seeds the library for the `benchmark` build type from a snapshot pushed to a fixed shared
 * location (see [DeviceBenchmarkLayout]), once at startup and outside the measured loop. Every
 * other build type is a no-op.
 *
 * This is the app-side half of the snapshot-ingest capability: the host generates the snapshot
 * with `perfbench snapshot`, the developer pushes it (and the corpus audio) under shared external
 * storage (e.g. `/sdcard/Music/chipbox-test/`, beside where a real library would live), and the app
 * ingests it here before the UI composes — so a device benchmark starts from a warm, deterministic
 * library instead of a scan. Reading the shared location needs All Files Access
 * (`MANAGE_EXTERNAL_STORAGE`); the benchmark grants it. Import is idempotent; a snapshot hash is
 * cached so a re-launch that finds the same snapshot already imported skips the (no-op) merge.
 */
internal object BenchmarkLibrarySeed {
    private const val PREFS = "benchmark_seed"
    private const val KEY_SNAPSHOT_HASH = "library_snapshot_sha256"

    @Suppress("TooGenericExceptionCaught") // a bad fixture must never crash the app; degrade to empty
    fun seedIfBenchmark(context: Context, repository: Repository, hatchet: Hatchet) {
        if (!BuildConfig.BENCHMARK) return

        val root = File(Environment.getExternalStorageDirectory(), DeviceBenchmarkLayout.ROOT_DIR)
        val snapshotFile = File(root, DeviceBenchmarkLayout.SNAPSHOT_FILE_NAME)
        if (!snapshotFile.exists()) {
            hatchet.w("Benchmark seed: no pushed snapshot at ${snapshotFile.absolutePath}; library not seeded")
            return
        }
        try {
            importSnapshot(context, repository, hatchet, root, snapshotFile)
        } catch (e: Exception) {
            // The shared location is outside the app sandbox: without All Files Access the read
            // throws, as does a corrupt fixture. A benchmark run must not crash the app over a
            // missing snapshot — it just starts from an empty library.
            hatchet.w("Benchmark seed: could not import ${snapshotFile.absolutePath}: $e")
        }
    }

    private fun importSnapshot(
        context: Context,
        repository: Repository,
        hatchet: Hatchet,
        root: File,
        snapshotFile: File,
    ) {
        val hash = sha256Hex(snapshotFile.path)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_SNAPSHOT_HASH, null) == hash) {
            hatchet.d("Benchmark seed: snapshot $hash already imported; skipping")
            return
        }

        // Blocking: the seed must complete before the first composition so a measured scroll never
        // races an empty library. It runs at process start, outside the benchmark's measured block.
        runBlocking {
            val snapshot = LibrarySnapshotJson.decodeFromString<LibrarySnapshot>(snapshotFile.readText())
            val corpusRoot = File(root, DeviceBenchmarkLayout.CORPUS_DIR_NAME).absolutePath
            val result = repository.importLibrarySnapshot(snapshot, root = corpusRoot)
            prefs.edit().putString(KEY_SNAPSHOT_HASH, hash).apply()
            hatchet.d(
                "Benchmark seed: imported ${result.total} game(s) from $hash " +
                    "(${result.added} added, ${result.updated} updated) — corpus root $corpusRoot",
            )
        }
    }
}
