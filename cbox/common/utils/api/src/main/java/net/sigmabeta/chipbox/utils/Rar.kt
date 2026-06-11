package net.sigmabeta.chipbox.utils

import com.github.junrar.Archive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * JVM/Android: junrar decodes RAR 4.x, including the solid, best-compressed archives RSN sets ship
 * as. Entries are extracted in iteration order, which solid archives require (each member's decode
 * depends on the running unpack state of the ones before it). Any failure — not a RAR, an
 * unsupported RAR5 archive, a truncated stream — returns null; the decode miss is the error signal
 * the caller logs.
 */
@Suppress("SwallowedException", "TooGenericExceptionCaught", "DEPRECATION")
actual fun unrar(bytes: ByteArray): List<RarEntry>? = try {
    Archive(ByteArrayInputStream(bytes)).use { archive ->
        buildList {
            var header = archive.nextFileHeader()
            while (header != null) {
                if (!header.isDirectory) {
                    val out = ByteArrayOutputStream(header.fullUnpackSize.toInt().coerceAtLeast(0))
                    archive.extractFile(header, out)
                    add(RarEntry(header.fileName.trim(), out.toByteArray()))
                }
                header = archive.nextFileHeader()
            }
        }
    }
} catch (e: Exception) {
    null
}
