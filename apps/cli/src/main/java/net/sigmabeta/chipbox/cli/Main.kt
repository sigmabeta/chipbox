package net.sigmabeta.chipbox.cli

import com.github.ajalt.mordant.terminal.Terminal
import java.io.File

/**
 * Chipbox CLI entry point. Opens the main menu, which drives every action — view / rescan / add /
 * remove / exit — against a library persisted under [WORK_DIR_NAME] in the working directory.
 */
fun main() {
    val terminal = Terminal()
    val workDir = File(System.getProperty("user.dir"), WORK_DIR_NAME).apply { mkdirs() }
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

/** Library DB + persisted scan locations live under this dir in the working directory. */
private const val WORK_DIR_NAME = ".chipbox-cli"
