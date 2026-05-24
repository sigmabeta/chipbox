package net.sigmabeta.chipbox.cli

import com.github.ajalt.mordant.input.interactiveSelectList
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * "Organize Library" flow. Picks a destination library location (only when more than one is
 * saved), computes the proposed `$destination/$platform/$game/` layout, lets the user navigate it,
 * and on a confirmed commit moves the files into place.
 */
class OrganizeLibrary(
    private val terminal: Terminal,
    private val library: ChipboxLibrary,
) {
    private val organizer = LibraryOrganizer()

    fun run() {
        val destination = chooseDestination() ?: return
        val moves = runBlocking {
            organizer.plan(library.gamesWithTracks(), destination, library.savedLocations().toSet())
        }
        if (moves.isEmpty()) {
            terminal.println("Nothing to organize.")
            return
        }
        LibraryBrowser(terminal).browse(proposalTree(moves), title = PROPOSAL_TITLE, exitLabel = CANCEL_ROW)
    }

    private fun chooseDestination(): File? {
        val locations = library.savedLocations()
        if (locations.size == 1) return File(locations.first())
        val choice = terminal.interactiveSelectList(locations + CANCEL_ROW, title = DESTINATION_TITLE)
        return if (choice == null || choice == CANCEL_ROW) null else File(choice)
    }

    private fun proposalTree(moves: List<FolderMove>): List<MenuNode> {
        val grouped = moves.groupBy { it.category }
        // Platforms alphabetical; the "Invalid Folders" bucket always sorts last.
        val sortedKeys = grouped.keys.sortedWith(compareBy({ it == INVALID_CATEGORY }, { it }))
        val categories = sortedKeys.map { category ->
            val group = grouped.getValue(category)
            MenuNode("$category (${group.size})") { group.map { folderNode(it) } }
        }
        return listOf(commitNode(moves)) + categories
    }

    private fun folderNode(move: FolderMove): MenuNode =
        MenuNode("${move.folderName} (${move.entries.size})") {
            move.entries.map { MenuNode(it.name) }
        }

    private fun commitNode(moves: List<FolderMove>): MenuNode =
        MenuNode(COMMIT_ROW, onSelect = { confirmAndCommit(moves) })

    private fun confirmAndCommit(moves: List<FolderMove>): Boolean {
        val choice = terminal.interactiveSelectList(
            listOf(CONFIRM_NO, CONFIRM_YES),
            title = "Move ${moves.size} folder(s) now? Back up your library first — this can't be undone.",
        )
        if (choice != CONFIRM_YES) return false

        val result = organizer.commit(moves)
        val failure = if (result.failedFolders > 0) ", ${result.failedFolders} failed" else ""
        terminal.println("Organized ${result.movedFolders} folder(s)$failure.")
        // Files moved, so the database paths are now stale; rescan to pick up the new locations.
        terminal.println(gray("Refreshing the database with the new paths…"))
        LibraryScan(terminal, library).run()
        return true
    }

    private companion object {
        const val PROPOSAL_TITLE = "Proposed layout — choose Commit to apply"
        const val DESTINATION_TITLE = "Choose destination library location"
        const val COMMIT_ROW = "✓ Commit organization"
        const val CANCEL_ROW = "← Cancel"
        const val CONFIRM_NO = "No, cancel"
        const val CONFIRM_YES = "Yes — I have a backup, move the files"
    }
}
