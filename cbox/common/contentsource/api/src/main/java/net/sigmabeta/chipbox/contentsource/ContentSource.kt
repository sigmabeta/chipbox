package net.sigmabeta.chipbox.contentsource

interface ContentSource {
    val sourceId: String
    suspend fun openBytes(identifier: String): ByteArray?
}