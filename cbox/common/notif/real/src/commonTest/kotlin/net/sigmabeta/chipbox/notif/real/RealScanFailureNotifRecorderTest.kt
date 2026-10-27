package net.sigmabeta.chipbox.notif.real

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.notif.NotifCategory
import net.sigmabeta.chipbox.notif.fake.FakeNotifManager
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.chipbox.scanner.state.ScanFailureKind
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealScanFailureNotifRecorderTest {

    @Test
    fun `a scan failure becomes an error notification`() = runTest {
        val scanner = CountingScanner(UnconfinedTestDispatcher(testScheduler))
        val notifManager = FakeNotifManager()
        val recorder = RealScanFailureNotifRecorder(
            scanner = scanner,
            notifManager = notifManager,
            stringProvider = stubStringProvider(),
            hatchet = BluntHatchet(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        recorder.observe()

        scanner.pushState(ScannerState.Failed(path = "bad-folder/track.spc"))

        val notif = notifManager.notifState.value.notifs.values.single()
        assertEquals(RealScanFailureNotifRecorder.NOTIF_ID, notif.id)
        assertEquals(NotifCategory.ERROR, notif.category)
        assertTrue(notif.isOneTime, "A stale failure notice should not survive the next launch")
        assertTrue("bad-folder/track.spc" in notif.description)

        recorder.release()
    }

    @Test
    fun `a configuration failure uses the dedicated description with no path`() = runTest {
        val scanner = CountingScanner(UnconfinedTestDispatcher(testScheduler))
        val notifManager = FakeNotifManager()
        val recorder = RealScanFailureNotifRecorder(
            scanner = scanner,
            notifManager = notifManager,
            stringProvider = stubStringProvider(),
            hatchet = BluntHatchet(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        recorder.observe()

        scanner.pushState(
            ScannerState.Failed(
                path = "Overlapping library locations",
                kind = ScanFailureKind.CONFIGURATION,
            ),
        )

        val notif = notifManager.notifState.value.notifs.values.single()
        assertEquals(
            ChipboxStringId.NOTIF_SCAN_FAILED_CONFIGURATION_DESCRIPTION.toString(),
            notif.description,
        )

        recorder.release()
    }

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = "$string:$arg"
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
