package net.sigmabeta.chipbox.features.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.capabilities.AppCapabilities
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.contentsource.fake.FakeLibrarySource
import net.sigmabeta.chipbox.notif.NotifManager
import net.sigmabeta.chipbox.notif.fake.FakeNotifManager
import net.sigmabeta.chipbox.player.director.fake.FakeDirector
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.SectionListModel
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The browser never manages its library: Home's empty state must explain the server-side flow and
 * must not offer folder/rescan CTAs that would dead-end or mutate server-owned state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeCapabilitiesTest {

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `server-managed empty home shows the server copy and no CTAs`() = runTest {
        val vm = newVm(capabilities = AppCapabilities(canManageLibrary = false))
        val state = vm.state.first { it.hasTracks == false }

        val container = state.toListItems(stubStringProvider()).filterIsInstance<SectionListModel>().single()
        val emptyState = container.sectionItems.filterIsInstance<EmptyStateListModel>().single()
        assertEquals(ChipboxStringId.HOME_EMPTY_SERVER_MANAGED.toString(), emptyState.explanation)
        assertTrue(container.sectionItems.filterIsInstance<CtaListModel>().isEmpty())
    }

    @Test
    fun `view model seeds canManageLibrary from capabilities`() = runTest {
        val managed = newVm(capabilities = AppCapabilities(canManageLibrary = false))
        val local = newVm(capabilities = AppCapabilities(canManageLibrary = true))

        assertFalse(managed.state.first().canManageLibrary)
        assertTrue(local.state.first().canManageLibrary)
    }

    @Test
    fun `server-managed home ignores add-folder and rescan actions`() = runTest {
        val scanner = CountingScanner()
        val vm = newVm(
            capabilities = AppCapabilities(canManageLibrary = false),
            scanner = scanner,
        )

        assertNull(collectOrNull(vm, HomeAction.AddFolderClicked))
        assertNull(collectOrNull(vm, HomeAction.RescanLibraryClicked))
        assertEquals(0, scanner.startCount)
    }

    private fun newVm(
        capabilities: AppCapabilities = AppCapabilities(),
        repository: Repository = FakeRepository(emptyMap()),
        director: FakeDirector = FakeDirector(),
        librarySource: LibrarySource = FakeLibrarySource(),
        scanner: Scanner = CountingScanner(),
        notifManager: NotifManager = FakeNotifManager(),
    ) = HomeViewModel(
        capabilities = capabilities,
        modules = emptySet(),
        repository = repository,
        director = director,
        librarySource = librarySource,
        scanner = scanner,
        notifManager = notifManager,
        stringProvider = stubStringProvider(),
        hatchet = BluntHatchet(),
    )

    private suspend fun CoroutineScope.collectOrNull(vm: HomeViewModel, action: SageAction): ChipboxEvent? =
        withTimeoutOrNull(50) {
            coroutineScope {
                async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
                    .also { vm.sendAction(action) }
                    .await()
            }
        }

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
