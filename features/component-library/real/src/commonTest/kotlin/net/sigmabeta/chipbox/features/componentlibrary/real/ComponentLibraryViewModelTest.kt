package net.sigmabeta.chipbox.features.componentlibrary.real

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.common.ui.components.api.NowPlayingHomeCardListModel
import net.sigmabeta.chipbox.common.ui.components.api.ScanCardStatus
import net.sigmabeta.chipbox.common.ui.components.api.ScanStatusCardListModel
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import net.sigmabeta.sage.ui.componentlibrary.ComponentLibraryAction
import net.sigmabeta.sage.ui.componentlibrary.ComponentLibraryConfig
import net.sigmabeta.sage.ui.componentlibrary.ComponentLibraryLayout
import net.sigmabeta.sage.ui.componentlibrary.componentLibraryItems
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComponentLibraryViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a gallery action changes the config`() {
        val vm = ComponentLibraryViewModel(stubStringProvider(), BluntHatchet())

        vm.sendAction(ComponentLibraryAction.SetLayout(ComponentLibraryLayout.GRID))

        assertEquals(ComponentLibraryLayout.GRID, vm.state.value.config.layout)
    }

    @Test
    fun `an unrelated action leaves the state alone`() {
        val vm = ComponentLibraryViewModel(stubStringProvider(), BluntHatchet())
        val before = vm.state.value

        vm.sendAction(SageAction.Noop)

        assertEquals(before, vm.state.value)
    }

    @Test
    fun `column type follows the layout`() {
        ComponentLibraryLayout.entries.forEach { layout ->
            val state = ComponentLibraryState(ComponentLibraryConfig(layout = layout))
            assertEquals(layout.columnType, state.columnType)
        }
        assertEquals(ColumnType.One, ComponentLibraryState().columnType)
    }

    @Test
    fun `the chipbox cards are in the gallery`() {
        val items = ComponentLibraryState().toListItems(stubStringProvider())

        assertTrue(items.any { it is NowPlayingHomeCardListModel }, "now-playing card present")
        val scanStatuses = items.filterIsInstance<ScanStatusCardListModel>().map { it.status }
        assertEquals(ScanCardStatus.entries.toSet(), scanStatuses.toSet(), "every scan status present")
    }

    @Test
    fun `the catalog accepts chipbox's extras without duplicate ids`() {
        // componentLibraryItems throws IllegalStateException on any repeated dataId.
        ComponentLibraryLayout.entries.forEach { layout ->
            componentLibraryItems(ComponentLibraryConfig(layout = layout), chipboxSampleCards())
        }
    }

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
