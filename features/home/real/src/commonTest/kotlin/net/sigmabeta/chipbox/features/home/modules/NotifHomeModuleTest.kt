package net.sigmabeta.chipbox.features.home.modules

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.notif.Notif
import net.sigmabeta.chipbox.notif.NotifCategory
import net.sigmabeta.chipbox.notif.fake.FakeNotifManager
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.NotifListModel
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotifHomeModuleTest {

    @Test
    fun `hidden with no notifications`() = runTest {
        val module = NotifHomeModule(FakeNotifManager(), stubStringProvider())
        assertEquals(LCE.Uninitialized, module.state().first())
    }

    @Test
    fun `renders every notification with its error styling`() = runTest {
        val manager = FakeNotifManager(
            initialNotifs = listOf(
                notif(id = 1L, category = NotifCategory.ERROR, description = "scan failed"),
                notif(id = 2L, category = NotifCategory.INFO, description = "what's new"),
            ),
        )
        val module = NotifHomeModule(manager, stubStringProvider())

        val lce = module.state().first()
        assertTrue(lce is LCE.Content)
        val items = lce.data.items.filterIsInstance<NotifListModel>()
        assertEquals(listOf(1L, 2L), items.map { it.dataId })
        assertEquals(ChipboxStringId.NOTIF_SCAN_FAILED_TITLE.toString(), items[0].title)
        assertEquals(listOf(true, false), items.map { it.isError })
        assertEquals(listOf("scan failed", "what's new"), items.map { it.description })
    }

    private fun notif(id: Long, category: NotifCategory, description: String) = Notif(
        id = id,
        title = ChipboxStringId.NOTIF_SCAN_FAILED_TITLE,
        description = description,
        category = category,
    )

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
