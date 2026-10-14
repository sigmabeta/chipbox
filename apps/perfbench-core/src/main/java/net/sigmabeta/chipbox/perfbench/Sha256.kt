package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.security.MessageDigest
import java.util.HexFormat

private const val HASH_BUFFER_BYTES = 8192
private val HEX = HexFormat.of()

/**
 * The lowercase hex SHA-256 of the file at [path], streamed through the digest rather than read
 * whole, so a large corpus file is never held in memory.
 *
 * Shared by the host corpus-manifest builder and the app's benchmark library seed, so a file
 * hashes to the same value through either call site. Lives in the `jvmSharedMain` source set
 * (`src/main/java`) because it uses `java.*`; both the `jvm` and Android targets resolve it.
 */
fun sha256Hex(path: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    File(path).inputStream().buffered(HASH_BUFFER_BYTES).use { input ->
        val buffer = ByteArray(HASH_BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return HEX.formatHex(digest.digest())
}
