package net.sigmabeta.chipbox.jvm

import net.sigmabeta.chipbox.contentsource.ContentSource
import java.io.File

/**
 * Single [ContentSource] id the JVM target stamps onto every scanned track — the
 * `ContentSourceRegistry` looks up the source by id, and the player's stager reads bytes
 * through it. The scanner and the play wiring both reference this so they stay in sync.
 */
internal const val SOURCE_ID = "file"

/**
 * Minimal [ContentSource] for the headless JVM target: the track's `path` is an absolute
 * filesystem path, so resolving it is just reading the file. The real Android app resolves
 * content-URI / SAF identifiers here instead; on the desktop a plain path is enough.
 */
class FileContentSource(override val sourceId: String) : ContentSource {
    override suspend fun openBytes(identifier: String): ByteArray? {
        val file = File(identifier)
        return if (file.isFile) file.readBytes() else null
    }
}
