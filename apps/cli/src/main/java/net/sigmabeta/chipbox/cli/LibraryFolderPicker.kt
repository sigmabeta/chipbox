package net.sigmabeta.chipbox.cli

import com.github.ajalt.mordant.input.interactiveSelectList
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File

/**
 * Interactive folder browser built on Mordant's [interactiveSelectList]. Each step lists the
 * current directory's subfolders plus two control rows — open the parent, or commit the current
 * directory as the library — and re-renders after each choice until the user commits a folder or
 * aborts (q / Esc, which Mordant reports as a null selection).
 */
class LibraryFolderPicker(private val terminal: Terminal) {
    /** Returns the chosen library folder, or `null` if the user aborted the picker. */
    fun choose(start: File = defaultStartDirectory()): File? {
        var current = start
        var chosen: File? = null
        var aborted = false

        while (chosen == null && !aborted) {
            val subDirs = current.readableSubDirectories()
            val parentRow = if (current.parentFile != null) PARENT_ROW else null
            val entries = listOfNotNull(parentRow, USE_ROW) + subDirs.map { it.name + DIR_SUFFIX }

            val choice = terminal.interactiveSelectList(
                entries,
                title = "Choose your library folder — ${current.path}",
            )

            when (choice) {
                null -> aborted = true
                PARENT_ROW -> current = current.parentFile ?: current
                USE_ROW -> chosen = current
                else -> current = subDirs.first { it.name + DIR_SUFFIX == choice }
            }
        }

        return chosen
    }

    /** Visible, readable subdirectories of [this], sorted case-insensitively by name. */
    private fun File.readableSubDirectories(): List<File> =
        listFiles { file -> file.isDirectory && file.canRead() && !file.isHidden }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    private companion object {
        const val PARENT_ROW = ".. (parent)"
        const val USE_ROW = "[ Use this folder ]"
        const val DIR_SUFFIX = "/"

        /** Start at the user's home directory, falling back to the process working directory. */
        fun defaultStartDirectory(): File {
            val home = System.getProperty("user.home")?.let(::File)?.takeIf(File::isDirectory)
            return (home ?: File(System.getProperty("user.dir"))).absoluteFile
        }
    }
}
