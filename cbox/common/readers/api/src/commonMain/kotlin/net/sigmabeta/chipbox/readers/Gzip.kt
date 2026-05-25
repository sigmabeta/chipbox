package net.sigmabeta.chipbox.readers

/**
 * Decompress gzip [bytes], or null if they aren't valid gzip / the platform can't. gzip lives in
 * `java.util.zip` on the JVM and has no common-stdlib equivalent (okio's `GzipSource` is
 * JVM/Native only), so it's an expect/actual.
 */
internal expect fun gunzip(bytes: ByteArray): ByteArray?
