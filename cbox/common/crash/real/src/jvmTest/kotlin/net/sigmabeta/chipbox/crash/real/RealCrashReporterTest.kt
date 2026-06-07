package net.sigmabeta.chipbox.crash.real

import net.sigmabeta.chipbox.crash.CrashReport
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.logging.HatchetError
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests [RealCrashReporter]. The reporter mutates process-global state (the default uncaught
 * handler), so each test saves and restores it. Crashes are simulated by pulling the handler the
 * reporter installed and calling it directly — no real thread death required.
 */
class RealCrashReporterTest {
    private lateinit var crashDir: File
    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    @BeforeTest
    fun setUp() {
        crashDir = Files.createTempDirectory("crash-test").toFile()
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
    }

    @AfterTest
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        crashDir.deleteRecursively()
    }

    @Test
    fun `serializes a full crash report with stack trace and recent errors`() {
        val hatchet = FakeHatchet(
            listOf(HatchetError(timestamp = 100L, tag = "Loader", thread = "io", message = "load failed")),
        )
        val reporter = newReporter(hatchet = hatchet, now = { 12_345L })
        reporter.install()

        val boom = IllegalStateException("kaboom")
        currentHandler().uncaughtException(Thread.currentThread(), boom)

        val report = readReport(File(crashDir, "crash-12345.json"))
        assertEquals(12_345L, report.timestampMs)
        assertEquals("java.lang.IllegalStateException", report.exceptionClass)
        assertEquals("kaboom", report.message)
        assertTrue("IllegalStateException" in report.stackTrace, "stack trace should be captured")
        assertEquals("1.2.3", report.appVersionName)
        assertEquals("beta", report.appBuildBranch)
        assertEquals(1, report.recentErrors.size)
        assertEquals("load failed", report.recentErrors.single().message)
        assertEquals("Loader", report.recentErrors.single().tag)
    }

    @Test
    fun `delegates to the previously-installed handler`() {
        var delegatedTo: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> delegatedTo = throwable }

        val reporter = newReporter()
        reporter.install()

        val boom = RuntimeException("downstream")
        currentHandler().uncaughtException(Thread.currentThread(), boom)

        assertEquals(boom, delegatedTo, "the prior handler must still run so the process tears down normally")
    }

    @Test
    fun `install is idempotent`() {
        val reporter = newReporter()
        reporter.install()
        val afterFirst = currentHandler()
        reporter.install()

        assertEquals(afterFirst, currentHandler(), "a second install() must not stack another handler")
    }

    @Test
    fun `prunes to the most recent reports`() {
        var clock = 0L
        val reporter = newReporter(now = { clock })
        reporter.install()
        val handler = currentHandler()

        repeat(25) {
            clock = it.toLong()
            handler.uncaughtException(Thread.currentThread(), RuntimeException("crash $it"))
        }

        val remaining = crashDir.listFiles { file -> file.name.endsWith(".json") }!!.map { it.name }.sorted()
        assertEquals(20, remaining.size, "only the newest 20 reports should remain")
        assertTrue(remaining.none { it == "crash-0.json" }, "the oldest reports should be pruned")
        assertTrue("crash-24.json" in remaining, "the newest report should survive")
    }

    private fun newReporter(
        hatchet: Hatchet = FakeHatchet(emptyList()),
        now: () -> Long = { 0L },
    ) = RealCrashReporter(
        crashDir = crashDir,
        appInfo = AppInfo(
            isDebug = true,
            versionName = "1.2.3",
            versionCode = 7,
            buildTimeMs = null,
            buildBranch = "beta",
        ),
        hatchet = hatchet,
        now = now,
    )

    private fun currentHandler(): Thread.UncaughtExceptionHandler =
        assertNotNull(Thread.getDefaultUncaughtExceptionHandler(), "reporter should have installed a handler")

    private fun readReport(file: File): CrashReport {
        assertTrue(file.exists(), "expected a crash report at ${file.name}")
        return Json.decodeFromString(CrashReport.serializer(), file.readText())
    }

    private class FakeHatchet(override val recentErrors: List<HatchetError>) : Hatchet {
        override fun v(message: String) = Unit
        override fun d(message: String) = Unit
        override fun i(message: String) = Unit
        override fun w(message: String) = Unit
        override fun e(message: String) = Unit
        override fun log(severity: Int, message: String) = Unit
    }
}
