package net.sigmabeta.chipbox.cli

import com.github.ajalt.mordant.input.interactiveSelectList
import com.github.ajalt.mordant.rendering.TextColors.brightCyan
import com.github.ajalt.mordant.rendering.TextColors.brightGreen
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.scanner.state.ScannerState

/**
 * Top-level CLI menu. Loops over the available actions, re-evaluating which apply each time:
 * View needs a scanned database, Rescan needs at least one saved location; Add, Remove, and Exit
 * are always offered. Returns when the user picks Exit (or aborts with q / Esc).
 */
class MainMenu(
    private val terminal: Terminal,
    private val library: ChipboxLibrary,
) {
    fun run() {
        while (true) {
            val choice = terminal.interactiveSelectList(menuRows(), title = TITLE)
            if (choice == null || choice == EXIT_ROW) break
            dispatch(choice)
        }
    }

    private fun menuRows(): List<String> = buildList {
        val hasDatabase = library.hasDatabase()
        val hasLocations = library.savedLocations().isNotEmpty()
        if (hasDatabase) add(VIEW_ROW)
        if (hasLocations) add(RESCAN_ROW)
        if (hasDatabase && hasLocations) add(ORGANIZE_ROW)
        add(ADD_ROW)
        add(REMOVE_ROW)
        add(EXIT_ROW)
    }

    private fun dispatch(choice: String) {
        when (choice) {
            VIEW_ROW -> viewLibrary()
            RESCAN_ROW -> rescan()
            ORGANIZE_ROW -> OrganizeLibrary(terminal, library).run()
            ADD_ROW -> addLocation()
            REMOVE_ROW -> removeLocation()
            else -> Unit
        }
    }

    private fun viewLibrary() {
        val root = runBlocking { LibraryMenu(library).root() }
        LibraryBrowser(terminal).browse(root)
    }

    private fun rescan() {
        val locations = library.savedLocations()
        terminal.println("Scanning ${locations.size} saved library location(s):")
        locations.forEach { terminal.println("  ${gray(it)}") }

        val state = runBlocking {
            library.scan { name, trackCount ->
                terminal.println("  ${brightGreen("+")} $name ${gray("($trackCount tracks)")}")
            }
        }
        printScanResult(state)
    }

    private fun addLocation() {
        val folder = LibraryFolderPicker(terminal).choose() ?: return
        library.addLibraryFolder(folder)
        terminal.println("Added ${brightCyan(folder.path)} to the library.")
    }

    private fun removeLocation() {
        val locations = library.savedLocations()
        if (locations.isEmpty()) {
            terminal.println("No library locations to remove.")
            return
        }
        val choice = terminal.interactiveSelectList(locations + CANCEL_ROW, title = REMOVE_TITLE)
        if (choice == null || choice == CANCEL_ROW) return
        library.removeLibraryFolder(choice)
        terminal.println("Removed ${brightCyan(choice)} from the library.")
    }

    private fun printScanResult(state: ScannerState) = when (state) {
        is ScannerState.Complete -> terminal.println(
            "Scan complete: ${state.gamesFound} games, ${state.tracksFound} tracks " +
                "(${state.tracksFailed} failed) in ${state.timeInSeconds}s.",
        )

        is ScannerState.Failed -> terminal.println("Scan failed at ${state.path}.")

        else -> Unit
    }

    private companion object {
        const val TITLE = "Chipbox"
        const val REMOVE_TITLE = "Remove which location?"
        const val VIEW_ROW = "View library"
        const val RESCAN_ROW = "Rescan library"
        const val ORGANIZE_ROW = "Organize library"
        const val ADD_ROW = "Add library location"
        const val REMOVE_ROW = "Remove library location"
        const val EXIT_ROW = "Exit"
        const val CANCEL_ROW = "← Cancel"
    }
}
