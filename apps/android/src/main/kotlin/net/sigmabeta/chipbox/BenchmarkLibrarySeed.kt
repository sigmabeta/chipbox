package net.sigmabeta.chipbox

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import net.sigmabeta.chipbox.perfbench.DeviceBenchmarkLayout
import net.sigmabeta.chipbox.repository.LibrarySnapshot
import net.sigmabeta.chipbox.repository.LibrarySnapshotJson
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.importLibrarySnapshot
import net.sigmabeta.sage.logging.Hatchet

/**
 * Seeds the library for the `benchmark` build type from a snapshot pushed to a fixed external
 * location (see [DeviceBenchmarkLayout]), once at startup and outside the measured loop. Every
 * other build type is a no-op.
 *
 * This is the app-side half of the snapshot-ingest capability: the host generates the snapshot
 * with `perfbench snapshot`, the developer pushes it (and the corpus audio) under the app's
 * external files dir, and the app ingests it here before the UI composes — so a device benchmark
 * starts from a warm, deterministic library instead of a scan. Import is idempotent; a snapshot
 * hash is cached so a re-launch that finds the same snapshot already imported skips the (no-op)
 * merge entirely.
 */
internal object BenchmarkLibrarySeed {
    private const val PREFS = "benchmark_seed"
    private const val KEY_SNAPSHOT_HASH = "library_snapshot_sha256"

    fun seedIfBenchmark(context: Context, repository: Repository, hatchet: Hatchet) {
        if (!BuildConfig.BENCHMARK) return

        val root = context.getExternalFilesDir(DeviceBenchmarkLayout.ROOT_DIR)
        val snapshotFile = root?.let { File(it, DeviceBenchmarkLayout.SNAPSHOT_FILE_NAME) }
        if (root == null || snapshotFile == null || !snapshotFile.exists()) {
            hatchet.w("Benchmark seed: no pushed snapshot found; library not seeded")
        } else {
            importSnapshot(context, repository, hatchet, root, snapshotFile)
        }
    }

    private fun importSnapshot(
        context: Context,
        repository: Repository,
        hatchet: Hatchet,
        root: File,
        snapshotFile: File,
    ) {
        val hash = snapshotFile.sha256()
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

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(readBytes())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
