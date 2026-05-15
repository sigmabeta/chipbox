package net.sigmabeta.chipbox.player.cache.real

import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.sage.logging.Hatchet
import java.io.File

/**
 * Writes a track's source bytes plus any chain files (e.g. PSF/2SF `_lib` siblings) into
 * [stagingTrackDir] and returns the path to the main file. Native emulators only accept a
 * filesystem path, so every factory in this module stages before opening an emulator.
 *
 * The directory is wiped before staging and is owned by the caller for cleanup — typically
 * the [EmulatorPcmSource] that holds the emulator deletes it on close.
 */
internal suspend fun stageTrack(
    track: Track,
    ext: String,
    mainBytes: ByteArray,
    stagingTrackDir: File,
    contentSourceRegistry: ContentSourceRegistry,
    hatchet: Hatchet,
): File {
    // Guard against the extension carrying path separators or being empty — the staged filename
    // is "main.<ext>" and unsanitised values would smuggle directories into the cache layout.
    require(ext.isNotEmpty() && !ext.contains('/') && !ext.contains('\\')) {
        "Refusing to stage track ${track.id}: invalid extension '$ext'."
    }
    val dir = stagingTrackDir.apply {
        deleteRecursively()
        mkdirs()
    }
    val mainFile = File(dir, "main.$ext").apply { writeBytes(mainBytes) }

    if (track.chainFiles.isNotEmpty()) {
        val source = contentSourceRegistry.get(track.source)
            ?: error(
                "No content source '${track.source}' — cannot stage ${track.chainFiles.size}" +
                    " chain file(s) for track ${track.id}."
            )
        for (chain in track.chainFiles) {
            val chainBytes = source.openBytes(chain.uri)
                ?: error("Failed to read chain file '${chain.filename}' for track ${track.id}.")
            File(dir, chain.filename).writeBytes(chainBytes)
            hatchet.v("Staged chain file ${chain.filename} (${chainBytes.size} bytes).")
        }
    }

    hatchet.v(
        "Staged track ${track.id} to ${mainFile.absolutePath} (+${track.chainFiles.size} chain file(s))."
    )
    return mainFile
}
