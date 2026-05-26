package net.sigmabeta.chipbox.features.managelibrary

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.contentsource.LibraryLocationInfo
import net.sigmabeta.chipbox.contentsource.fake.FakeLibrarySource
import net.sigmabeta.chipbox.features.rescanstatus.RescanStatus
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the three actions [ManageLibraryViewModel] handles — AddFolderClicked,
 * FolderPicked, FolderClicked — plus the `init` collector that mirrors [LibrarySource]'s
 * `locations` StateFlow into the visible folder list.
 *
 * [Scanner] is an abstract class (not an interface) in production, so the fake subclasses it
 * with a no-op `scan()` body and surfaces a call counter — the only behaviour the VM cares
 * about is `scanner.startScan()` being invoked after a folder is picked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManageLibraryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init populates the folder list from LibrarySource locations`() = runTest {
        val source = FakeLibrarySource(initial = listOf(
            LibraryLocationInfo("file:///music/SNES", "SNES"),
            LibraryLocationInfo("file:///music/NES", "NES"),
        ))
        val vm = newViewModel(source = source)
        val state = vm.state.first { it.folders.size == 2 }
        assertEquals(
            listOf(
                LibraryFolder("file:///music/SNES", "SNES"),
                LibraryFolder("file:///music/NES", "NES"),
            ),
            state.folders,
        )
    }

    @Test
    fun `LibrarySource location changes flow into the visible folder list`() = runTest {
        // The VM holds the collect over the source's StateFlow for life — a later add should
        // re-emit and the state should re-render.
        val source = FakeLibrarySource()
        val vm = newViewModel(source = source)
        assertTrue(vm.state.first().folders.isEmpty())

        source.setLocations(listOf(LibraryLocationInfo("file:///music/Genesis", "Genesis")))

        val state = vm.state.first { it.folders.size == 1 }
        assertEquals("Genesis", state.folders.single().displayName)
    }

    @Test
    fun `AddFolderClicked emits PickFolder`() = runTest {
        val vm = newViewModel()
        val event = collectAndDispatch(vm, ManageLibraryAction.AddFolderClicked)
        assertEquals(ChipboxEvent.PickFolder, event)
    }

    @Test
    fun `FolderPicked adds the location, starts a scan, and navigates to RescanStatus`() = runTest {
        val source = FakeLibrarySource()
        val scanner = CountingScanner(dispatcher)
        val vm = newViewModel(source = source, scanner = scanner)

        val event = collectAndDispatch(
            vm,
            ManageLibraryAction.FolderPicked("file:///music/PSX"),
        )

        // All three effects fired in order.
        assertEquals(listOf("file:///music/PSX"), source.addedLocations)
        assertEquals(1, scanner.startCount, "starting a scan is the second effect after picking a folder")
        assertEquals(ChipboxEvent.NavigateTo(RescanStatus), event)
    }

    @Test
    fun `FolderClicked removes the location and surfaces a confirmation snackbar`() = runTest {
        val source = FakeLibrarySource(
            initial = listOf(LibraryLocationInfo("file:///music/PSX", "PSX")),
        )
        val vm = newViewModel(source = source)

        val event = collectAndDispatch(
            vm,
            ManageLibraryAction.FolderClicked("file:///music/PSX"),
        )

        assertEquals(listOf("file:///music/PSX"), source.removedLocations)
        assertTrue(event is ChipboxEvent.ShowSnackbar)
        assertEquals("Folder removed from library.", event.message)
    }

    // ---- helpers ----

    private fun newViewModel(
        source: FakeLibrarySource = FakeLibrarySource(),
        scanner: CountingScanner = CountingScanner(dispatcher),
    ) = ManageLibraryViewModel(
        librarySource = source,
        scanner = scanner,
        stringProvider = stubStringProvider(),
        hatchet = BluntHatchet(),
    )

    /** Subscribe to events first, dispatch the action, await the resulting event. */
    private suspend fun CoroutineScope.collectAndDispatch(
        vm: ManageLibraryViewModel,
        action: ManageLibraryAction,
    ): ChipboxEvent = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
        .also { vm.sendAction(action) }
        .await()

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
