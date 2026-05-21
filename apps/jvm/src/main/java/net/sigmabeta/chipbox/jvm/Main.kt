package net.sigmabeta.chipbox.jvm

import dev.zacsweers.metro.createGraphFactory
import java.io.File
import net.sigmabeta.chipbox.jvm.di.JvmChipboxGraph

/** Library DB + render staging live under .chipbox-jvm in the working directory. */
private const val WORK_DIR_NAME = ".chipbox-jvm"
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
 * Build the Metro graph for this run. The DB file + render-cache workdir live under
 * `<user.dir>/.chipbox-jvm` so a run is self-contained. Metro's `@SingleIn(AppScope::class)`
 * makes accessors lazy enough that the window opens before the DB does.
 */
internal fun buildGraph(): JvmChipboxGraph {
    val workingDir = File(System.getProperty("user.dir"))
    val workDir = File(workingDir, WORK_DIR_NAME).apply { mkdirs() }
    return createGraphFactory<JvmChipboxGraph.Factory>().create(
        dbPath = File(workDir, LIBRARY_DB_NAME).absolutePath,
        workDir = workDir,
    )
}
