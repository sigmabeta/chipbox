package net.sigmabeta.chipbox.jvm

import net.sigmabeta.chipbox.models.ChainFile
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.sage.logging.Hatchet
import java.io.File

/**
 * Minimal file-walker scanner for the JVM target. Walks a directory, groups supported music
 * files by their parent folder (one [RawGame] per folder), and stages any `*lib` siblings as
 * chain files so mini-formats resolve their `_lib`. No metadata extraction — track titles
 * come from filenames, game titles from folder names. A richer scanner that reuses the
 * Android `RealScanner`'s [net.sigmabeta.chipbox.readers] tag/PSF logic comes later, once
 * the scanner is decoupled from `AndroidFileContentSource`.
 */
class JvmLibraryScanner(
    private val emulators: List<Emulator>,
    private val hatchet: Hatchet,
) {
    private val supportedExtensions: Set<String> =
        emulators.flatMap { it.supportedFileExtensions }.map { it.lowercase() }.toSet()

    suspend fun scan(root: File, repository: Repository) {
        require(root.isDirectory) { "Scan root is not a directory: ${root.absolutePath}" }
        hatchet.i("Scanning ${root.absolutePath} (${supportedExtensions.size} known extensions)…")

        val byDir: Map<File, List<File>> = root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in supportedExtensions }
            .groupBy { it.parentFile ?: root }

        var gameCount = 0
        var trackCount = 0
        for ((dir, files) in byDir) {
            val chainFiles = dir.listFiles { f -> f.isFile && f.extension.lowercase().endsWith("lib") }
                ?.map { ChainFile(filename = it.name, uri = it.absolutePath) }
                .orEmpty()

            // file-is-the-track for every format the JVM target plays today (SPC, PSF, VGM,
            // mini*). Each file gets `trackNumber = 0` because that's what the native
            // emulators expect for single-track loads — multi-subtrack formats (NSF/GBS/…)
            // would need one Track per subtrack, which this minimal walker doesn't do yet.
            val tracks = files.sortedBy { it.name }.map { file ->
                RawTrack(
                    path = file.absolutePath,
                    source = SOURCE_ID,
                    title = file.nameWithoutExtension,
                    artist = "",
                    game = dir.name,
                    length = DEFAULT_TRACK_LENGTH_MS,
                    trackNumber = 0,
                    fadeLengthMs = DEFAULT_FADE_LENGTH_MS,
                    chainFiles = chainFiles,
                    extension = file.extension.lowercase(),
                    platform = Platform.OTHER,
                )
            }

            repository.addGame(RawGame(title = dir.name, photoUrl = null, tracks = tracks))
            gameCount++
            trackCount += tracks.size
        }

        hatchet.i("Scan complete: $gameCount game(s), $trackCount track(s) added.")
    }

    companion object {
        /** Default per-track render length when no metadata reader has run yet. */
        private const val DEFAULT_TRACK_LENGTH_MS = 60_000L
        private const val DEFAULT_FADE_LENGTH_MS = 2_000L
    }
}
