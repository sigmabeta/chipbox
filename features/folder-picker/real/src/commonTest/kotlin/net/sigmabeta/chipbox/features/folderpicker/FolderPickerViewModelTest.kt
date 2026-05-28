package net.sigmabeta.chipbox.features.folderpicker

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
 * Exercises the three [FolderPickerAction] handlers plus the init-time descent into the
 * default path. [FakeFolderLister] keeps the test off disk — the VM only touches the abstraction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FolderPickerViewModelTest {

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
    fun `init descends into the default path and lifts its entries plus fileCount into state`() = runTest {
        val lister = FakeFolderLister(
            mapOf(
                "/root" to FolderListing(
                    folders = listOf(FolderPickerEntry("Music", "/root/Music", 2, 3)),
                    fileCount = 4,
                ),
            ),
        )
        val vm = newViewModel(defaultPath = "/root", lister = lister)

        val state = vm.state.first { it.currentPath == "/root" }
        assertEquals(listOf(FolderPickerEntry("Music", "/root/Music", 2, 3)), state.entries)
        assertEquals(4, state.fileCount)
    }

    @Test
    fun `FolderClicked replaces state with the clicked folder's listing — no navigation event`() = runTest {
        val lister = FakeFolderLister(
            mapOf(
                "/root" to FolderListing(listOf(FolderPickerEntry("Music", "/root/Music", 1, 0)), 0),
                "/root/Music" to FolderListing(listOf(FolderPickerEntry("PSF", "/root/Music/PSF", 0, 0)), 7),
            ),
        )
        val vm = newViewModel(defaultPath = "/root", lister = lister)
        vm.state.first { it.currentPath == "/root" }

        // FolderClicked is in-place — it must not emit a NavigateTo. Async event collector with
        // a tiny suspend window proves no event arrives before state flips to the new path.
        vm.sendAction(FolderPickerAction.FolderClicked("/root/Music"))
        val state = vm.state.first { it.currentPath == "/root/Music" }
        assertEquals(listOf(FolderPickerEntry("PSF", "/root/Music/PSF", 0, 0)), state.entries)
        assertEquals(7, state.fileCount)
    }

    @Test
    fun `AddThisFolderClicked adds currentPath to LibrarySource, starts a scan, and navigates to RescanStatus`() = runTest {
        val source = FakeLibrarySource()
        val scanner = CountingScanner(dispatcher)
        val vm = newViewModel(
            defaultPath = "/root/Music",
            lister = FakeFolderLister(mapOf("/root/Music" to FolderListing(emptyList(), 0))),
            source = source,
            scanner = scanner,
        )
        vm.state.first { it.currentPath == "/root/Music" }

        val event = collectAndDispatch(vm, FolderPickerAction.AddThisFolderClicked)
        assertEquals(listOf("/root/Music"), source.addedLocations)
        assertEquals(1, scanner.startCount)
        assertEquals(ChipboxEvent.NavigateTo(RescanStatus), event)
    }

    @Test
    fun `CancelClicked emits NavigateBack and does not touch the library`() = runTest {
        val source = FakeLibrarySource()
        val vm = newViewModel(
            defaultPath = "/root",
            lister = FakeFolderLister(mapOf("/root" to FolderListing(emptyList(), 0))),
            source = source,
        )
        val event = collectAndDispatch(vm, FolderPickerAction.CancelClicked)
        assertEquals(ChipboxEvent.NavigateBack, event)
        assertTrue(source.addedLocations.isEmpty(), "Cancel must not commit anything")
    }

    // ---- helpers ----

    private fun newViewModel(
        defaultPath: String,
        lister: FolderLister,
        source: FakeLibrarySource = FakeLibrarySource(),
        scanner: CountingScanner = CountingScanner(dispatcher),
    ) = FolderPickerViewModel(
        defaultPath = defaultPath,
        folderLister = lister,
        librarySource = source,
        scanner = scanner,
        stringProvider = stubStringProvider(),
        hatchet = BluntHatchet(),
    )

    private suspend fun CoroutineScope.collectAndDispatch(
        vm: FolderPickerViewModel,
        action: FolderPickerAction,
    ): ChipboxEvent = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
        .also { vm.sendAction(action) }
        .await()

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String =
            string.toString()
    }

    private class FakeFolderLister(
        private val listings: Map<String, FolderListing>,
    ) : FolderLister {
        override fun list(path: String): FolderListing =
            listings[path] ?: FolderListing(emptyList(), 0)
    }
}
