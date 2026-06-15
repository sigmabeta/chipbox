package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.errorlog.ErrorLog
import kotlin.test.Test

/** The debug error log renders under its title. Pure display, no actions. */
class ErrorLogTest {
    @Test
    fun showsTitle() = runChipboxUiTest {
        startAtScreen(ErrorLog)

        assertTitle("Error log")
    }
}
