package net.sigmabeta.chipbox.utils

/** One extracted member of a RAR archive: its in-archive [name] and decompressed [bytes]. */
class RarEntry(val name: String, val bytes: ByteArray)

/**
 * Decompress a RAR archive into its file entries, in archive order, or null when [bytes] aren't a
 * readable RAR or the platform has no RAR decoder. RAR has no common-stdlib decoder, so this is an
 * expect/actual: the JVM backs it with junrar; JS returns null (the same shape as gzip/`.vgz`).
 *
 * Solid archives decode sequentially — each member's unpack depends on the running state of the
 * ones before it — so the actual must extract entries in iteration order.
 */
expect fun unrar(bytes: ByteArray): List<RarEntry>?
