package net.sigmabeta.chipbox.cli

import com.github.ajalt.mordant.terminal.Terminal
import net.sigmabeta.chipbox.utils.appDataDir

/**
 * Chipbox CLI entry point. Opens the main menu, which drives every action — view / rescan / add /
 * remove / exit — against a library persisted under a standardized per-OS application-data
 * directory in the user's home (see [appDataDir]), so it finds the same library regardless of the
 * working directory it's launched from.
 */
fun main() {
    val terminal = Terminal()
    val workDir = appDataDir(xdgName = "chipbox-cli", nativeName = "Chipbox CLI").apply { mkdirs() }
    val library = ChipboxLibrary(workDir)
    try {
        MainMenu(terminal, library).run()
    } catch (expected: IllegalStateException) {
        // Mordant throws this when stdin isn't an interactive TTY (piped input, or the Gradle
        // daemon). The menu can't work there, so point at the installed distribution.
        terminal.println(
            "Can't show the menu: ${expected.message}. Run the installed CLI in a real " +
                "terminal — apps/cli/build/install/chipbox-cli/bin/chipbox-cli.",
        )
    } finally {
        library.close()
    }
}
