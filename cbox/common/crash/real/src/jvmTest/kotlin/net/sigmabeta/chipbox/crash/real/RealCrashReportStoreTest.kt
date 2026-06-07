package net.sigmabeta.chipbox.crash.real

import net.sigmabeta.chipbox.crash.CrashReport
import net.sigmabeta.sage.logging.BluntHatchet
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests [RealCrashReportStore]. Reports are written straight to disk in the on-disk format so the
 * store is exercised independently of [RealCrashReporter]'s handler plumbing.
 */
class RealCrashReportStoreTest {
    private lateinit var crashDir: File
    private val store get() = RealCrashReportStore(crashDir, BluntHatchet())

    @BeforeTest
    fun setUp() {
        crashDir = Files.createTempDirectory("crash-store-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        crashDir.deleteRecursively()
    }

    @Test
    fun `list returns nothing when the directory is empty or missing`() {
        assertTrue(store.list().isEmpty())
        crashDir.delete()
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `list returns persisted reports newest-first`() {
        writeReport(timestampMs = 100L, message = "older")
        writeReport(timestampMs = 300L, message = "newest")
        writeReport(timestampMs = 200L, message = "middle")

        val messages = store.list().map { it.message }
        assertEquals(listOf("newest", "middle", "older"), messages)
    }

    @Test
    fun `list skips corrupt files but keeps the good ones`() {
        writeReport(timestampMs = 100L, message = "good")
        File(crashDir, "crash-200.json").writeText("{ not valid json")

        val reports = store.list()
        assertEquals(1, reports.size)
        assertEquals("good", reports.single().message)
    }

    @Test
    fun `list ignores files that are not crash reports`() {
        writeReport(timestampMs = 100L, message = "real")
        File(crashDir, "notes.txt").writeText("ignore me")
        File(crashDir, "crash-200.json.tmp").writeText("half-written")

        assertEquals(listOf("real"), store.list().map { it.message })
    }

    @Test
    fun `clear deletes only crash report files`() {
        writeReport(timestampMs = 100L, message = "doomed")
        val keep = File(crashDir, "keep.txt").apply { writeText("survivor") }

        store.clear()

        assertTrue(store.list().isEmpty())
        assertTrue(keep.exists(), "non-report files should be left alone")
    }

    private fun writeReport(timestampMs: Long, message: String) {
        val report = CrashReport(
            timestampMs = timestampMs,
            threadName = "main",
            exceptionClass = "java.lang.IllegalStateException",
            message = message,
            stackTrace = "java.lang.IllegalStateException: $message",
            appVersionName = "1.2.3",
            appVersionCode = 7,
            appBuildBranch = "beta",
            isDebugBuild = true,
            recentErrors = emptyList(),
        )
        File(crashDir, CrashReportFiles.fileName(timestampMs))
            .writeText(Json.encodeToString(CrashReport.serializer(), report))
    }
}
