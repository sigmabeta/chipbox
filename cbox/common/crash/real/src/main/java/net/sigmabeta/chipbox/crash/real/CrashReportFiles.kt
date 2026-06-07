package net.sigmabeta.chipbox.crash.real

import java.io.File

/**
 * The on-disk naming scheme for crash reports, shared by the writer ([RealCrashReporter]) and the
 * reader ([RealCrashReportStore]) so the two can't drift on what a report file is called.
 */
internal object CrashReportFiles {
    private val NAME_PATTERN = Regex("""crash-\d+\.json""")

    fun fileName(timestampMs: Long): String = "crash-$timestampMs.json"

    /** The report files in [dir], unsorted; empty if the dir is missing or unreadable. */
    fun reportsIn(dir: File): List<File> =
        dir.listFiles { file -> file.isFile && file.name.matches(NAME_PATTERN) }?.toList() ?: emptyList()
}
