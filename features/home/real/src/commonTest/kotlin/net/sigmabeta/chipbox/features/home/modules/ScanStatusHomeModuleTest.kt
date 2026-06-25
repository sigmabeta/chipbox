package net.sigmabeta.chipbox.features.home.modules

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.common.ui.components.api.ScanCardStatus
import net.sigmabeta.chipbox.common.ui.components.api.ScanStatusCardListModel
import net.sigmabeta.chipbox.common.ui.components.api.ScanStatusDetail
import net.sigmabeta.chipbox.features.home.HomeAction
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.chipbox.scanner.state.ScannerEvent
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.ImageNameCaptionListModel
import net.sigmabeta.sage.components.LabelValueListModel
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScanStatusHomeModuleTest {

    private val dispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())

    @Test
    fun `hidden while the scanner is idle`() = moduleTest { _, lces ->
        // No scan has started — the replayed Unknown state keeps the module Uninitialized.
        advanceTimeBy(SAMPLE_MS * 2)
        runCurrent()
        assertTrue(lces.all { it is LCE.Uninitialized })
    }

    @Test
    fun `scanning shows the status, current file, and the live changes`() = moduleTest { scanner, lces ->
        scanner.pushState(ScannerState.Scanning(timeInSeconds = 2))
        scanner.pushEvent(ScannerEvent.FileScanned(name = "song.spc"))
        scanner.pushEvent(ScannerEvent.GameFoundEvent(id = 9L, name = "MyGame", trackCount = 5, imageUrl = null))
        advanceTimeBy(SAMPLE_MS * 2)
        runCurrent()

        val card = lces.lastCard()
        assertEquals(ScanCardStatus.SCANNING, card.status)
        // Status word is wrapped by the "Library Scan …" title template.
        assertEquals(
            "${ChipboxStringId.HOME_SCAN_TITLE}:${ChipboxStringId.HOME_SCAN_STATUS_SCANNING}",
            card.statusLabel,
        )
        assertEquals("song.spc", card.currentFile)
        assertNull(card.dismissAction, "An in-progress scan can't be dismissed")
        val change = card.rows().filterIsInstance<ImageNameCaptionListModel>().single()
        assertEquals("MyGame", change.name)
        assertEquals(HomeAction.GameClicked(9L), change.clickAction)
    }

    @Test
    fun `complete shows the summary and drops the current file`() = moduleTest { scanner, lces ->
        scanner.pushState(ScannerState.Complete(timeInSeconds = 60, gamesFound = 10, tracksFound = 200, tracksFailed = 4))
        advanceTimeBy(SAMPLE_MS * 2)
        runCurrent()

        val card = lces.lastCard()
        assertEquals(ScanCardStatus.COMPLETE, card.status)
        assertNull(card.currentFile, "The file marquee is scanning-only")
        assertEquals(HomeAction.ScanStatusDismissed, card.dismissAction)
        // Elapsed / games / tracks / failed.
        assertEquals(4, card.rows().filterIsInstance<LabelValueListModel>().size)
    }

    @Test
    fun `failed shows a human-readable error row`() = moduleTest { scanner, lces ->
        scanner.pushState(ScannerState.Failed(path = "/sdcard/Music/bad.spc"))
        advanceTimeBy(SAMPLE_MS * 2)
        runCurrent()

        val card = lces.lastCard()
        assertEquals(ScanCardStatus.FAILED, card.status)
        assertEquals(HomeAction.ScanStatusDismissed, card.dismissAction)
        val error = card.detail as ScanStatusDetail.Error
        // The failing path is woven into the error template.
        assertEquals("${ChipboxStringId.HOME_SCAN_STATUS_ERROR}:/sdcard/Music/bad.spc", error.message)
    }

    @Test
    fun `a fresh scan clears the previous run's changes`() = moduleTest { scanner, lces ->
        scanner.pushState(ScannerState.Scanning(timeInSeconds = 1))
        scanner.pushEvent(ScannerEvent.GameFoundEvent(id = 1L, name = "Old", trackCount = 1, imageUrl = null))
        advanceTimeBy(SAMPLE_MS * 2)
        runCurrent()
        assertEquals(1, lces.lastCard().rows().filterIsInstance<ImageNameCaptionListModel>().size)

        scanner.pushState(ScannerState.Complete(timeInSeconds = 2, gamesFound = 1, tracksFound = 1, tracksFailed = 0))
        advanceTimeBy(SAMPLE_MS * 2)
        runCurrent()
        // New run begins — the old change must not carry over.
        scanner.pushState(ScannerState.Scanning(timeInSeconds = 0))
        advanceTimeBy(SAMPLE_MS * 2)
        runCurrent()

        assertTrue(lces.lastCard().rows().filterIsInstance<ImageNameCaptionListModel>().isEmpty())
    }

    // ---- helpers ----

    private fun moduleTest(
        block: suspend TestScope.(CountingScanner, List<LCE<HomeModuleSection>>) -> Unit,
    ) = runTest(dispatcher) {
        val scanner = CountingScanner()
        val module = ScanStatusHomeModule(scanner, stubStringProvider())
        val lces = mutableListOf<LCE<HomeModuleSection>>()
        val job: Job = launch { module.state().collect { lces.add(it) } }
        runCurrent()
        try {
            block(scanner, lces)
        } finally {
            job.cancel()
        }
    }

    private fun List<LCE<HomeModuleSection>>.lastCard(): ScanStatusCardListModel {
        val content = last { it is LCE.Content } as LCE.Content
        return content.data.items.single() as ScanStatusCardListModel
    }

    private fun ScanStatusCardListModel.rows() = (detail as ScanStatusDetail.Rows).items

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()

        // Keep the arg visible so the prefixed status title ("…TITLE" + status word) is assertable.
        override fun getStringOneArg(string: SageStringId, arg: String): String = "$string:$arg"
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }

    private companion object {
        /** Mirrors the module's private REFRESH_MS — the sample cadence the card is throttled to. */
        const val SAMPLE_MS = 500L
    }
}
