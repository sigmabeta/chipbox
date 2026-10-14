package net.sigmabeta.chipbox.perfbench

import java.io.File
import java.security.MessageDigest

private const val HASH_BUFFER_BYTES = 8192
private const val BYTE_MASK = 0xFF
private const val HIGH_NIBBLE_SHIFT = 4
private const val LOW_NIBBLE_MASK = 0x0F
private val HEX_DIGITS = "0123456789abcdef".toCharArray()

/**
 * The lowercase hex SHA-256 of the file at [path], streamed through the digest rather than read
 * whole, so a large corpus file is never held in memory.
 *
 * Shared by the host corpus-manifest builder and the app's benchmark library seed, so a file
 * hashes to the same value through either call site. Lives in the `jvmSharedMain` source set
 * (`src/main/java`) because it uses `java.*`; both the `jvm` and Android targets resolve it.
 *
 * Hex is formatted by hand rather than with `java.util.HexFormat`, which only exists on Android
 * API 34+ and would otherwise crash the seed and the corpus pin on older devices.
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
    return toHex(digest.digest())
}

/** Lowercase hex of [bytes], two digits each, portable to every supported Android API level. */
private fun toHex(bytes: ByteArray): String {
    val hex = CharArray(bytes.size * 2)
    for (index in bytes.indices) {
        val value = bytes[index].toInt() and BYTE_MASK
        hex[index * 2] = HEX_DIGITS[value ushr HIGH_NIBBLE_SHIFT]
        hex[index * 2 + 1] = HEX_DIGITS[value and LOW_NIBBLE_MASK]
    }
    return String(hex)
}
