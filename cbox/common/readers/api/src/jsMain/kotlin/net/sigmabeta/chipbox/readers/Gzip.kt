package net.sigmabeta.chipbox.readers

/**
 * JS has no bundled gzip; `.vgz` isn't supported on this (enforcement-only) target, so callers
 * see it as a decompression miss.
 */
internal actual fun gunzip(bytes: ByteArray): ByteArray? = null
