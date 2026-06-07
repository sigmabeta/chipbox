package net.sigmabeta.chipbox.crash.real

import net.sigmabeta.chipbox.crash.CrashReport
import net.sigmabeta.chipbox.crash.CrashReporter
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.logging.HatchetError
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.Json

/**
 * [CrashReporter] for the JVM-family targets (Android + desktop): both have `java.lang.Thread` and
 * `java.io.File`, so the impl lives in `jvmSharedMain` (`src/main/java`) rather than per-platform.
 *
 * On [install] it registers a process-wide uncaught-exception handler. When one fires, the work has
 * to finish *before* the process dies, so the write is fully synchronous — the shared [Storage]
 * abstraction is async (DataStore / a launched disk write) and can't promise the bytes hit disk in
 * the sliver of time a dying process gets. Each crash becomes its own JSON file (so concurrent or
 * back-to-back crashes don't clobber each other), written to a temp file and atomically moved into
 * place so a second crash mid-write can't leave a half-written report behind. After writing, the
 * handler delegates to whatever handler was installed before it, preserving the platform's default
 * crash behaviour (Android's dialog, the JVM's stderr dump).
 */
class RealCrashReporter(
    private val crashDir: File,
    private val appInfo: AppInfo,
    private val hatchet: Hatchet,
    private val now: () -> Long = System::currentTimeMillis,
) : CrashReporter {
    private val installed = AtomicBoolean(false)
    private val json = Json { prettyPrint = true }

    override fun install() {
        // compareAndSet keeps install() idempotent and avoids stacking handlers if it's wired into
        // more than one startup path.
        if (!installed.compareAndSet(false, true)) return

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Never let a failure here swallow the original crash — persisting is best-effort; the
            // delegate below is what actually tears the process down.
            runCatching { writeReport(thread, throwable) }
                .onFailure { hatchet.e("CrashReporter failed to persist a crash report: $it") }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeReport(thread: Thread, throwable: Throwable) {
        val report = CrashReport(
            timestampMs = now(),
            threadName = thread.name,
            exceptionClass = throwable.javaClass.name,
            message = throwable.message,
            // stackTraceToString() walks the full cause chain, so nested causes survive in the file.
            stackTrace = throwable.stackTraceToString(),
            appVersionName = appInfo.versionName,
            appVersionCode = appInfo.versionCode,
            appBuildBranch = appInfo.buildBranch,
            isDebugBuild = appInfo.isDebug,
            recentErrors = hatchet.recentErrors.map(::toRecentError),
        )

        crashDir.mkdirs()
        val target = File(crashDir, CrashReportFiles.fileName(report.timestampMs))
        val tmp = File(crashDir, "${target.name}.tmp")
        tmp.writeText(json.encodeToString(CrashReport.serializer(), report))
        moveIntoPlace(tmp, target)
        pruneOldReports()
    }

    private fun moveIntoPlace(tmp: File, target: File) {
        runCatching {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.onFailure {
            // Some filesystems can't do an atomic move across the rename; a plain rename still beats
            // leaving the report stranded in the temp file.
            tmp.renameTo(target)
        }
    }

    /** Keep only the most recent [MAX_REPORTS] crash files so the directory can't grow unbounded. */
    private fun pruneOldReports() {
        CrashReportFiles.reportsIn(crashDir)
            .sortedByDescending { it.name }
            .drop(MAX_REPORTS)
            .forEach { it.delete() }
    }

    private fun toRecentError(error: HatchetError) = CrashReport.RecentError(
        timestampMs = error.timestamp,
        tag = error.tag,
        thread = error.thread,
        message = error.message,
    )

    private companion object {
        const val MAX_REPORTS = 20
    }
}
