package net.sigmabeta.chipbox.cli

import com.github.ajalt.mordant.rendering.TextColors.brightGreen
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.scanner.state.ScannerState

/**
 * Runs a full library scan over the saved locations, printing each game as it's found and a final
 * summary. Shared by the "Rescan library" menu action and by "Organize library", which rescans
 * after moving files so the database picks up their new paths.
 */
class LibraryScan(
    private val terminal: Terminal,
    private val library: ChipboxLibrary,
) {
    fun run() {
        val locations = library.savedLocations()
        terminal.println("Scanning ${locations.size} saved library location(s):")
        locations.forEach { terminal.println("  ${gray(it)}") }

        val state = runBlocking {
            library.scan { name, trackCount ->
                terminal.println("  ${brightGreen("+")} $name ${gray("($trackCount tracks)")}")
            }
        }
        printResult(state)
    }

    private fun printResult(state: ScannerState) = when (state) {
        is ScannerState.Complete -> terminal.println(
            "Scan complete: ${state.gamesFound} games, ${state.tracksFound} tracks " +
                "(${state.tracksFailed} failed) in ${state.timeInSeconds}s.",
        )

        is ScannerState.Failed -> terminal.println("Scan failed at ${state.path}.")

        else -> Unit
    }
}
