package net.sigmabeta.chipbox.crash.real

import net.sigmabeta.chipbox.crash.CrashReport
import net.sigmabeta.chipbox.crash.CrashReportStore
import net.sigmabeta.sage.logging.Hatchet
import java.io.File
import kotlinx.serialization.json.Json

/**
 * [CrashReportStore] over the same directory [RealCrashReporter] writes to. Lives in `jvmSharedMain`
 * alongside the writer since both are `java.io.File` work shared by Android and desktop.
 *
 * A report written by a newer build (extra fields) still decodes thanks to `ignoreUnknownKeys`; a
 * genuinely corrupt file is logged and skipped rather than failing the whole list, so one bad write
 * can't hide every other crash.
 */
class RealCrashReportStore(
    private val crashDir: File,
    private val hatchet: Hatchet,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : CrashReportStore {
    override fun list(): List<CrashReport> =
        CrashReportFiles.reportsIn(crashDir)
            .mapNotNull { file -> readReport(file) }
            .sortedByDescending { it.timestampMs }

    override fun clear() {
        CrashReportFiles.reportsIn(crashDir).forEach { it.delete() }
    }

    private fun readReport(file: File): CrashReport? =
        runCatching { json.decodeFromString(CrashReport.serializer(), file.readText()) }
            .onFailure { hatchet.e("CrashReportStore failed to read ${file.name}: $it") }
            .getOrNull()
}
