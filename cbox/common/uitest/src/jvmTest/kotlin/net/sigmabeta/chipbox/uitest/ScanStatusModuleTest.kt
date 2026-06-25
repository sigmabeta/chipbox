package net.sigmabeta.chipbox.uitest

import kotlin.test.Test

/**
 * The scan-status Home card (ScanStatusModule). It's hidden until a scan is driven, then surfaces
 * the coarse status, the file being read, and the changes/summary/failure detail. Once settled
 * (complete/failed) a tap dismisses it.
 *
 * The card reacts to the fake [net.sigmabeta.chipbox.scanner.fake.CountingScanner] the harness
 * drives via beginScan / scanReadingFile / completeScan / failScan; the Home tab is already hosted
 * by the shell, so no navigation is needed. The card prepends at the top of the already-laid-out
 * Home list, so [revealScanCard] scrolls it into view before on-screen assertions/taps.
 */
class ScanStatusModuleTest {

    @Test
    fun hiddenWhenIdle() = runChipboxUiTest {
        // No scan driven — the default idle scanner keeps the card off Home.
        assertTextNotInRow("Library Scan Scanning")
        assertTextNotInRow("Library Scan Complete")
        assertTextNotInRow("Library Scan Failed")
    }

    @Test
    fun scanningShowsStatusAndCurrentFile() = runChipboxUiTest {
        beginScan()
        scanReadingFile("robotnik_theme.spc")

        waitForContent("Library Scan Scanning")
        revealScanCard()
        assertDisplayed("Library Scan Scanning")
        assertTextInRow("robotnik_theme.spc")
    }

    @Test
    fun completeShowsSummary() = runChipboxUiTest {
        completeScan(games = 10, tracks = 200)

        waitForContent("Library Scan Complete")
        revealScanCard()
        assertDisplayed("Library Scan Complete")
        // The summary mirrors the Rescan Status screen's progress labels.
        assertTextInRow("Games found")
    }

    @Test
    fun failedShowsErrorMessage() = runChipboxUiTest {
        failScan("/sdcard/Music/bad.spc")

        waitForContent("Library Scan Failed")
        revealScanCard()
        assertDisplayed("Library Scan Failed")
        assertTextInRow("The scan failed while reading /sdcard/Music/bad.spc.")
    }

    @Test
    fun tappingSettledCardDismissesIt() = runChipboxUiTest {
        completeScan(games = 1, tracks = 1)
        waitForContent("Library Scan Complete")
        revealScanCard()

        // A tap anywhere on the settled card clears the scan, hiding the card again.
        click("Library Scan Complete")
        waitForContentGone("Library Scan Complete")
    }
}
