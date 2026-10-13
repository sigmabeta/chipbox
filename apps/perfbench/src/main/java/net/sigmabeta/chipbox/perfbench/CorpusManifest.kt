package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.security.MessageDigest
import java.util.HexFormat

private const val HASH_BUFFER_BYTES = 8192
private val HEX = HexFormat.of()

/**
 * Builds the ordered corpus manifest for the files a run actually measured.
 *
 * Entries carry each file's path relative to [root] (always with `/` separators, so the manifest is
 * portable between a host corpus and a device's rebased copy), its SHA-256, and its size. Entries
 * are sorted by path, so two runs over the same corpus produce byte-identical manifests and the
 * comparability guard can match them exactly.
 */
internal fun corpusManifest(root: File, measured: Collection<File>): List<CorpusEntry> =
    measured.distinct().map { file ->
        CorpusEntry(
            path = file.relativeToOrSelf(root).invariantSeparatorsPath,
            sha256 = sha256(file),
            bytes = file.length(),
        )
    }.sortedBy { it.path }

/** The lowercase hex SHA-256 of [file], streamed so a large corpus file is never held in memory. */
internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered(HASH_BUFFER_BYTES).use { input ->
        val buffer = ByteArray(HASH_BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return HEX.formatHex(digest.digest())
}
