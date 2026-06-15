package net.sigmabeta.chipbox.uitest

import net.sigmabeta.chipbox.features.rescanstatus.RescanStatus
import kotlin.test.Test

/** Rescan status with no scan running (the default) shows its idle empty state. Pure display. */
class RescanStatusTest {
    @Test
    fun showsIdleState() = runChipboxUiTest {
        startAtScreen(RescanStatus)

        assertEmptyStateDisplayed("No scan in progress.")
    }
}
