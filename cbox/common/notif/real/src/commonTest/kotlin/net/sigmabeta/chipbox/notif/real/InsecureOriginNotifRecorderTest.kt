package net.sigmabeta.chipbox.notif.real

import net.sigmabeta.chipbox.notif.NotifCategory
import net.sigmabeta.chipbox.notif.fake.FakeNotifManager
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InsecureOriginNotifRecorderTest {

    @Test
    fun `an insecure origin becomes a one-time error notification`() {
        val notifManager = FakeNotifManager()

        val recorder = InsecureOriginNotifRecorder(
            notifManager = notifManager,
            stringProvider = stubStringProvider(),
            hatchet = BluntHatchet(),
            isSecureContext = { false },
        )
        recorder.observe()
        recorder.observe()

        val notif = notifManager.notifState.value.notifs.values.single()
        assertEquals(InsecureOriginNotifRecorder.NOTIF_ID, notif.id)
        assertEquals(ChipboxStringId.NOTIF_INSECURE_ORIGIN_TITLE, notif.title)
        assertEquals(ChipboxStringId.NOTIF_INSECURE_ORIGIN_DESCRIPTION.toString(), notif.description)
        assertEquals(NotifCategory.ERROR, notif.category)
        assertTrue(notif.isOneTime, "A stale secure-context warning should not survive the next launch")
    }

    @Test
    fun `a secure origin adds nothing`() {
        val notifManager = FakeNotifManager()

        InsecureOriginNotifRecorder(
            notifManager = notifManager,
            stringProvider = stubStringProvider(),
            hatchet = BluntHatchet(),
            isSecureContext = { true },
        ).observe()

        assertTrue(notifManager.notifState.value.notifs.isEmpty())
    }

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = "$string:$arg"
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
