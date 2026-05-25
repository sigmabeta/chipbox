package net.sigmabeta.chipbox.readers

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * JVM/Android: standard-library gzip via java.util.zip. A malformed/incomplete stream
 * (ZipException is an IOException) returns null — the decompress miss is the error signal, which
 * the caller (VgmReader) logs; nothing to rethrow here.
 */
@Suppress("SwallowedException")
internal actual fun gunzip(bytes: ByteArray): ByteArray? = try {
    GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
} catch (e: IOException) {
    null
}
