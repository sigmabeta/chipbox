package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.crashlog.CrashLog
import kotlin.test.Test

/** Crash log with no recorded crashes (the default) shows its empty state. Pure display, no actions. */
class CrashLogTest {
    @Test
    fun showsEmptyState() = runChipboxUiTest {
        startAtScreen(CrashLog)

        assertEmptyStateDisplayed("No crashes recorded.")
    }
}
