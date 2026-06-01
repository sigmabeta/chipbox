package net.sigmabeta.chipbox.abrender

import java.io.File

/**
 * Discovers renderable tracks by walking a caller-supplied corpus directory — no database, no
 * scanner. Chiptune corpora are laid out as `.../<Game>/<track>.<ext>`, so the parent folder names
 * the game and the filename names the track.
 *
 * Selection:
 * - default: one representative track per game = the alphabetically-first playable file in each
 *   leaf folder (a "game").
 * - [everySong]: every playable file.
 *
 * Files whose extension no backend supports (e.g. `.usflib` / `.psflib` shared libraries) are
 * skipped as tracks but stay on disk as siblings, so the native loader still resolves them.
 *
 * Multi-subsong formats (NSF, GBS, ...) expose only [subsong] here (default 0); full per-subsong
 * enumeration needs metadata this walker doesn't read.
 */
fun walkCorpus(
    root: File,
    supported: Set<String>,
    extensionFilter: Set<String>?,
    gameFilter: String?,
    everySong: Boolean,
    subsong: Int,
): List<SelectedTrack> {
    val playable = root.walkTopDown()
        .filter { it.isFile }
        .filter { file ->
            val ext = file.extension.lowercase()
            extensionFilter?.contains(ext) ?: (ext in supported)
        }
        .filter { file ->
            gameFilter == null || (file.parentFile?.name?.contains(gameFilter, ignoreCase = true) == true)
        }
        .toList()

    val chosen = if (everySong) {
        playable
    } else {
        // One per "game" = one per leaf folder; pick the first by name for determinism.
        playable.groupBy { it.parentFile?.absolutePath ?: it.absolutePath }
            .values
            .mapNotNull { filesInGame -> filesInGame.minByOrNull { it.name } }
    }

    return chosen
        .sortedBy { it.absolutePath }
        .map { file ->
            val relative = file.relativeToOrSelf(root).path
            SelectedTrack(
                trackKey = "$relative#$subsong",
                path = file.absolutePath,
                trackNumber = subsong,
                extension = file.extension.lowercase(),
                game = file.parentFile?.name ?: root.name,
                title = file.nameWithoutExtension,
            )
        }
}
