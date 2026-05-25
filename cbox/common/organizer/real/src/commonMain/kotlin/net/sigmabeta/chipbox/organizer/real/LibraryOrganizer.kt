package net.sigmabeta.chipbox.organizer.real

import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.organizer.FolderMove
import net.sigmabeta.chipbox.organizer.INVALID_CATEGORY
import net.sigmabeta.chipbox.organizer.OrganizeResult
import net.sigmabeta.sage.ui.StringProvider
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/**
 * Pure planning + execution for "Organize Library". [plan] turns the scanned games into a list of
 * folder moves toward `$destination/$platform/$game/`; a game whose tracks are split across more
 * than one folder is "invalid" and each of its folders is routed to
 * `$destination/Invalid Folders/$game-$index/` instead. [commit] performs the moves on disk.
 *
 * Files are walked and moved through the injected [fileSystem] (okio), so the logic is multiplatform.
 * [strings] resolves platform display names (the per-platform destination folders), so the layout
 * matches the labels the apps show. Both are caller-supplied, keeping this free of any single app's
 * wiring.
 */
class LibraryOrganizer(
    private val fileSystem: FileSystem,
    private val strings: StringProvider,
) {
    fun plan(games: List<Game>, destination: Path, libraryLocations: Set<String>): List<FolderMove> {
        val roots = libraryLocations.mapNotNull { canonicalOrNull(it.toPath()) }.toSet()
        val moves = mutableListOf<FolderMove>()
        val usedNamesByCategory = HashMap<String, MutableSet<String>>()
        val invalidFolders = mutableListOf<Pair<String, Path>>()

        for (game in games) {
            val tracks = game.tracks.orEmpty()
            if (tracks.isEmpty()) continue
            val title = sanitizeName(game.title)
            val folders = distinctFolders(tracks)
            if (folders.size == 1) {
                val category = strings.getString(mostCommonPlatform(tracks).stringId)
                val used = usedNamesByCategory.getOrPut(category) { mutableSetOf() }
                moves += folderMove(category, uniqueName(used, title), folders.first(), destination, roots)
            } else {
                folders.forEach { invalidFolders += title to it }
            }
        }

        // Invalid folders are numbered with a single running index so every target is unique.
        invalidFolders.forEachIndexed { index, (title, folder) ->
            moves += folderMove(INVALID_CATEGORY, "$title-${index + 1}", folder, destination, roots)
        }
        return moves
    }

    fun commit(moves: List<FolderMove>): OrganizeResult {
        var moved = 0
        var failed = 0
        for (move in moves) {
            runCatching { applyMove(move) }
                .onSuccess { moved++ }
                .onFailure { failed++ }
        }
        return OrganizeResult(moved, failed)
    }

    private fun folderMove(
        category: String,
        folderName: String,
        source: Path,
        destination: Path,
        roots: Set<String>,
    ): FolderMove {
        val sourceIsRoot = canonicalOrNull(source) in roots
        val children = fileSystem.listOrNull(source).orEmpty().sortedBy { it.name }
        // A library-location root isn't a game's own folder, so only its loose files belong to the
        // game; its subdirectories are other games and are left for their own moves.
        val entries = if (sourceIsRoot) children.filter { isRegularFile(it) } else children
        val dest = destination / category / folderName
        return FolderMove(category, folderName, source, dest, entries, sourceIsRoot)
    }

    private fun applyMove(move: FolderMove) {
        if (canonicalOrNull(move.source) == canonicalOrNull(move.destination)) return
        fileSystem.createDirectories(move.destination)
        for (entry in move.entries) {
            moveInto(entry, move.destination / entry.name)
        }
        if (!move.sourceIsRoot) deleteIfEmpty(move.source)
    }

    // Recursive so a directory move works even across filesystems (an atomic rename across stores
    // fails); files are moved one at a time and emptied dirs removed.
    private fun moveInto(source: Path, dest: Path) {
        if (isDirectory(source)) {
            fileSystem.createDirectories(dest)
            fileSystem.listOrNull(source).orEmpty().forEach { moveInto(it, dest / it.name) }
            fileSystem.delete(source)
        } else {
            dest.parent?.let { fileSystem.createDirectories(it) }
            if (fileSystem.exists(dest)) fileSystem.delete(dest)
            try {
                fileSystem.atomicMove(source, dest)
            } catch (_: IOException) {
                // Cross-filesystem move: okio can't rename atomically, so copy the bytes then drop the original.
                fileSystem.copy(source, dest)
                fileSystem.delete(source)
            }
        }
    }

    private fun deleteIfEmpty(folder: Path) {
        if (isDirectory(folder) && fileSystem.listOrNull(folder).orEmpty().isEmpty()) fileSystem.delete(folder)
    }

    private fun distinctFolders(tracks: List<Track>): List<Path> = tracks
        .mapNotNull { it.path.toPath().parent }
        .distinctBy { canonicalOrNull(it) ?: it.toString() }

    private fun mostCommonPlatform(tracks: List<Track>): Platform =
        tracks.groupingBy { it.platform }.eachCount().maxByOrNull { it.value }?.key ?: Platform.OTHER

    private fun uniqueName(used: MutableSet<String>, base: String): String {
        var name = base
        var counter = 1
        while (name in used) {
            counter++
            name = "$base-$counter"
        }
        used.add(name)
        return name
    }

    private fun sanitizeName(name: String): String {
        val cleaned = name
            .map { if (it.isISOControl() || it in ILLEGAL_CHARS) '_' else it }
            .joinToString("")
            .trim()
            .trimEnd('.')
        return cleaned.ifBlank { "Unknown" }
    }

    private fun isDirectory(path: Path): Boolean = fileSystem.metadataOrNull(path)?.isDirectory == true

    private fun isRegularFile(path: Path): Boolean = fileSystem.metadataOrNull(path)?.isRegularFile == true

    private fun canonicalOrNull(path: Path): String? =
        runCatching { fileSystem.canonicalize(path) }.getOrNull()?.toString()

    private companion object {
        val ILLEGAL_CHARS = "\\/:*?\"<>|".toSet()
    }
}
