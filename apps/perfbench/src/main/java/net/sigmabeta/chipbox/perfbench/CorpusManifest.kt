package net.sigmabeta.chipbox.perfbench

import java.io.File

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
            sha256 = sha256Hex(file.path),
            bytes = file.length(),
        )
    }.sortedBy { it.path }
