package net.sigmabeta.chipbox.jvm

import dev.zacsweers.metro.createGraphFactory
import java.io.File
import net.sigmabeta.chipbox.jvm.di.JvmChipboxGraph
import net.sigmabeta.chipbox.utils.appDataDir

private const val LIBRARY_DB_NAME = "library.sqlite"

/**
 * JVM/desktop entrypoint — opens the Compose Multiplatform window. The headless `scan` /
 * `play` CLI modes that used to live alongside `gui` were removed; this is the desktop app
 * only. Scanning a library is reached through the Settings screen's folder picker.
 */
fun main() {
    runDesktop(buildGraph())
}

/**
 * Build the Metro graph for this run. The settings file, library DB, and render-cache staging
 * dir all live under a standardized per-OS application-data directory in the user's home (see
 * [appDataDir]) so a run finds the same data regardless of the working directory it's launched
 * from. Metro's `@SingleIn(AppScope::class)` makes accessors lazy enough that the window opens
 * before the DB does.
 */
internal fun buildGraph(): JvmChipboxGraph {
    val workDir = appDataDir(xdgName = "chipbox", nativeName = "Chipbox").apply { mkdirs() }
    return createGraphFactory<JvmChipboxGraph.Factory>().create(
        dbPath = File(workDir, LIBRARY_DB_NAME).absolutePath,
        workDir = workDir,
    )
}
