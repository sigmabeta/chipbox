package net.sigmabeta.chipbox.features.crashlog.real

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.crash.CrashReport
import net.sigmabeta.chipbox.crash.CrashReportStore
import net.sigmabeta.sage.components.CollapsibleDetailsListModel
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CrashLogViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shows the empty state when no crashes are stored`() = runTest {
        val vm = CrashLogViewModel(stubStringProvider(), BluntHatchet(), FakeCrashReportStore(emptyList()))

        val items = vm.state.first().toListItems(stubStringProvider())
        assertTrue(items.single() is EmptyStateListModel)
    }

    @Test
    fun `renders one collapsible row per crash, newest-first order preserved`() = runTest {
        val store = FakeCrashReportStore(listOf(report(300L, "Newer"), report(100L, "Older")))
        val vm = CrashLogViewModel(stubStringProvider(), BluntHatchet(), store)

        val items = vm.state.first().toListItems(stubStringProvider())
        assertEquals(listOf(300L, 100L), items.map { (it as CollapsibleDetailsListModel).dataId })
    }

    @Test
    fun `row title carries the simple class name and details carry message, stack trace, and recent log`() = runTest {
        val crash = report(
            timestampMs = 42L,
            message = "kaboom",
            stackTrace = "java.lang.IllegalStateException: kaboom\n\tat Foo.bar(Foo.kt:1)",
            recentErrors = listOf(
                CrashReport.RecentError(timestampMs = 1L, tag = "Loader", thread = "io", message = "load failed"),
            ),
        )
        val vm = CrashLogViewModel(stubStringProvider(), BluntHatchet(), FakeCrashReportStore(listOf(crash)))

        val row = vm.state.first().toListItems(stubStringProvider()).single() as CollapsibleDetailsListModel
        assertTrue(row.title.startsWith("IllegalStateException"), "title should use the simple class name")
        val details = row.detailItems.joinToString("\n")
        assertTrue("kaboom" in details, "message should appear in the details")
        assertTrue("Foo.bar(Foo.kt:1)" in details, "stack trace should appear in the details")
        assertTrue("load failed" in details, "recent log context should appear in the details")
        assertTrue("v1.2.3 (beta)" in details, "build metadata should appear in the details")
    }

    private fun report(
        timestampMs: Long,
        message: String?,
        stackTrace: String = "trace",
        recentErrors: List<CrashReport.RecentError> = emptyList(),
    ) = CrashReport(
        timestampMs = timestampMs,
        threadName = "main",
        exceptionClass = "java.lang.IllegalStateException",
        message = message,
        stackTrace = stackTrace,
        appVersionName = "1.2.3",
        appVersionCode = 7,
        appBuildBranch = "beta",
        isDebugBuild = true,
        recentErrors = recentErrors,
    )

    private class FakeCrashReportStore(private val reports: List<CrashReport>) : CrashReportStore {
        override fun list(): List<CrashReport> = reports
        override fun clear() = Unit
    }

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
