package net.sigmabeta.chipbox.notif.real

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.notif.Notif
import net.sigmabeta.chipbox.notif.NotifCategory
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.storage.common.Storage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealNotifManagerTest {

    @Test
    fun `add and remove update the state`() = runTest {
        val manager = managerOver(InMemoryStorage())

        manager.addNotif(notif(1L))
        assertEquals(setOf(1L), manager.notifState.value.notifs.keys)

        manager.removeNotif(1L)
        assertTrue(manager.notifState.value.notifs.isEmpty())
    }

    @Test
    fun `one-time notifications are dropped on the next launch but persistent ones survive`() = runTest {
        val storage = InMemoryStorage()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        val beforeRestart = RealNotifManager(storage, scope, BluntHatchet())
        beforeRestart.addNotif(notif(1L, isOneTime = true))
        beforeRestart.addNotif(notif(2L, isOneTime = false))

        val afterRestart = RealNotifManager(storage, scope, BluntHatchet())
        assertEquals(setOf(2L), afterRestart.notifState.value.notifs.keys)
    }

    private fun managerOver(storage: Storage) = RealNotifManager(
        storage = storage,
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        hatchet = BluntHatchet(),
    )

    private fun notif(id: Long, isOneTime: Boolean = false) = Notif(
        id = id,
        title = ChipboxStringId.NOTIF_SCAN_FAILED_TITLE,
        description = "n$id",
        category = NotifCategory.ERROR,
        isOneTime = isOneTime,
    )
}

private class InMemoryStorage : Storage {
    private val strings = mutableMapOf<String, MutableStateFlow<String?>>()
    private val ints = mutableMapOf<String, MutableStateFlow<Int?>>()

    override fun saveString(key: String, value: String) {
        stringFlow(key).value = value
    }

    override fun savedStringFlow(key: String): Flow<String?> = stringFlow(key).asStateFlow()

    override fun saveInt(key: String, value: Int) {
        intFlow(key).value = value
    }

    override fun savedIntFlow(key: String): Flow<Int?> = intFlow(key).asStateFlow()

    private fun stringFlow(key: String) = strings.getOrPut(key) { MutableStateFlow(null) }

    private fun intFlow(key: String) = ints.getOrPut(key) { MutableStateFlow(null) }
}
