package net.sigmabeta.chipbox.perfbench

import java.io.File

/**
 * The corpus block for a device run seeded from the pushed corpus + snapshot: the ordered manifest
 * of every file under [corpusRoot] and the SHA-256 of the [snapshotFile] the library was seeded from.
 *
 * On the device [corpusRoot] is the rebased copy under shared external storage
 * ([DeviceBenchmarkLayout]); its path is recorded as [`CorpusRef.root`]. Hashing the on-device files
 * (rather than trusting a host-generated sidecar) is what makes the block describe the corpus the
 * app actually read, so the comparability guard refuses a run whose pushed corpus differs.
 *
 * The caller must be able to read shared storage — the macrobenchmark grants itself All Files
 * Access first (see `DeviceBenchmark.grantStorageAccess`); that read is untested by construction and
 * is verified on hardware. This function itself is pure `java.io` and unit-tested on the host.
 *
 * Throws when either input is missing, so an unseeded run fails loudly instead of recording an empty
 * manifest that would compare equal to any other unseeded run.
 */
fun corpusPin(corpusRoot: File, snapshotFile: File): CorpusRef {
    require(corpusRoot.isDirectory) { "Corpus root is not a directory: ${corpusRoot.absolutePath}" }
    require(snapshotFile.isFile) { "Library snapshot not found: ${snapshotFile.absolutePath}" }
    return CorpusRef(
        root = corpusRoot.absolutePath,
        manifest = corpusManifest(corpusRoot, corpusRoot.walkTopDown().filter { it.isFile }.toList()),
        snapshotHash = sha256Hex(snapshotFile.path),
    )
}
