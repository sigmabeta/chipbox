package net.sigmabeta.chipbox.cli

import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.sage.ui.StringProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Pure planning + execution for "Organize Library". [plan] turns the scanned games into a list of
 * folder moves toward `$destination/$platform/$game/`; a game whose tracks are split across more
 * than one folder is "invalid" and each of its folders is routed to
 * `$destination/Invalid Folders/$game-$index/` instead. [commit] performs the moves on disk.
 */
class LibraryOrganizer(
    private val strings: StringProvider = cliStringProvider,
) {
    fun plan(games: List<Game>, destination: File, libraryLocations: Set<String>): List<FolderMove> {
        val roots = libraryLocations.mapNotNull { canonicalOrNull(File(it)) }.toSet()
        val moves = mutableListOf<FolderMove>()
        val usedNamesByCategory = HashMap<String, MutableSet<String>>()
        val invalidFolders = mutableListOf<Pair<String, File>>()

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
        source: File,
        destination: File,
        roots: Set<String>,
    ): FolderMove {
        val sourceIsRoot = canonicalOrNull(source) in roots
        val children = source.listFiles()?.toList().orEmpty().sortedBy { it.name }
        // A library-location root isn't a game's own folder, so only its loose files belong to the
        // game; its subdirectories are other games and are left for their own moves.
        val entries = if (sourceIsRoot) children.filter { it.isFile } else children
        val dest = File(File(destination, category), folderName)
        return FolderMove(category, folderName, source, dest, entries, sourceIsRoot)
    }

    private fun applyMove(move: FolderMove) {
        if (canonicalOrNull(move.source) == canonicalOrNull(move.destination)) return
        move.destination.mkdirs()
        for (entry in move.entries) {
            moveInto(entry, File(move.destination, entry.name))
        }
        if (!move.sourceIsRoot) deleteIfEmpty(move.source)
    }

    // Recursive so a directory move works even across filesystems (Files.move of a non-empty dir
    // across stores fails); files are moved one at a time and emptied dirs removed.
    private fun moveInto(source: File, dest: File) {
        if (source.isDirectory) {
            dest.mkdirs()
            source.listFiles()?.forEach { moveInto(it, File(dest, it.name)) }
            source.delete()
        } else {
            dest.parentFile?.mkdirs()
            Files.move(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun deleteIfEmpty(folder: File) {
        if (folder.isDirectory && folder.list()?.isEmpty() == true) folder.delete()
    }

    private fun distinctFolders(tracks: List<Track>): List<File> = tracks
        .mapNotNull { File(it.path).parentFile }
        .distinctBy { canonicalOrNull(it) ?: it.absolutePath }

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

    private fun canonicalOrNull(file: File): String? = runCatching { file.canonicalPath }.getOrNull()

    private companion object {
        val ILLEGAL_CHARS = "\\/:*?\"<>|".toSet()
    }
}

/** Top-level destination bucket for games whose tracks are split across multiple folders. */
internal const val INVALID_CATEGORY = "Invalid Folders"

/** One folder's worth of files to relocate, from [source] into [destination]. */
data class FolderMove(
    val category: String,
    val folderName: String,
    val source: File,
    val destination: File,
    val entries: List<File>,
    val sourceIsRoot: Boolean,
)

data class OrganizeResult(val movedFolders: Int, val failedFolders: Int)
