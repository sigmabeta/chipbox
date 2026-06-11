package net.sigmabeta.chipbox.player.cache.real

import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.utils.RSN_EXTENSION
import net.sigmabeta.chipbox.utils.RSN_MEMBER_EXTENSION
import net.sigmabeta.chipbox.utils.rsnSpcMembers
import net.sigmabeta.sage.logging.Hatchet
import okio.FileSystem
import okio.Path

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
    stagingTrackDir: Path,
    fileSystem: FileSystem,
    contentSourceRegistry: ContentSourceRegistry,
    hatchet: Hatchet,
): Path {
    // Guard against the extension carrying path separators or being empty — the staged filename
    // is "main.<ext>" and unsanitised values would smuggle directories into the cache layout.
    require(ext.isNotEmpty() && !ext.contains('/') && !ext.contains('\\')) {
        "Refusing to stage track ${track.id}: invalid extension '$ext'."
    }
    fileSystem.deleteRecursively(stagingTrackDir, mustExist = false)
    fileSystem.createDirectories(stagingTrackDir)

    // RSN archives carry no playable bytes of their own — unpack the SPC member at this track's
    // subsong index and stage it as a plain `.spc` so the SPC emulator can load it directly. The
    // member order matches the scanner's RsnReader, so the index lines up with the scanned track.
    if (ext == RSN_EXTENSION) {
        val members = rsnSpcMembers(mainBytes)
            ?: error("Could not unpack RSN '${track.path}' for track ${track.id}.")
        val member = members.getOrNull(track.trackNumber)
            ?: error(
                "RSN '${track.path}' has no SPC at index ${track.trackNumber} " +
                    "(${members.size} member(s)) for track ${track.id}."
            )
        val mainFile = stagingTrackDir / "main.$RSN_MEMBER_EXTENSION"
        fileSystem.write(mainFile) { write(member.bytes) }
        hatchet.v(
            "Staged RSN member '${member.name}' (${member.bytes.size} bytes) for track ${track.id}."
        )
        return mainFile
    }

    val mainFile = stagingTrackDir / "main.$ext"
    fileSystem.write(mainFile) { write(mainBytes) }

    if (track.chainFiles.isNotEmpty()) {
        val source = contentSourceRegistry.get(track.source)
            ?: error(
                "No content source '${track.source}' — cannot stage ${track.chainFiles.size}" +
                    " chain file(s) for track ${track.id}."
            )
        for (chain in track.chainFiles) {
            val chainBytes = source.openBytes(chain.uri)
                ?: error("Failed to read chain file '${chain.filename}' for track ${track.id}.")
            fileSystem.write(stagingTrackDir / chain.filename) { write(chainBytes) }
            hatchet.v("Staged chain file ${chain.filename} (${chainBytes.size} bytes).")
        }
    }

    hatchet.v(
        "Staged track ${track.id} to $mainFile (+${track.chainFiles.size} chain file(s))."
    )
    return mainFile
}
