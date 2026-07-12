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
    fun `NavigateUpClicked descends into the listing's parentPath`() = runTest {
        val lister = FakeFolderLister(
            mapOf(
                "/root/Music" to FolderListing(
                    folders = listOf(FolderPickerEntry("PSF", "/root/Music/PSF", 0, 0)),
                    fileCount = 2,
                    parentPath = "/root",
                ),
                "/root" to FolderListing(
                    folders = listOf(FolderPickerEntry("Music", "/root/Music", 1, 2)),
                    fileCount = 0,
                    parentPath = "/",
                ),
            ),
        )
        val vm = newViewModel(defaultPath = "/root/Music", lister = lister)
        vm.state.first { it.currentPath == "/root/Music" }

        vm.sendAction(FolderPickerAction.NavigateUpClicked)
        val state = vm.state.first { it.currentPath == "/root" }
        assertEquals(listOf(FolderPickerEntry("Music", "/root/Music", 1, 2)), state.entries)
        assertEquals("/", state.parentPath)
    }

    @Test
    fun `NavigateUpClicked at a root with a null parent stays put`() = runTest {
        val lister = FakeFolderLister(
            mapOf("/" to FolderListing(emptyList(), 0, parentPath = null)),
        )
        val vm = newViewModel(defaultPath = "/", lister = lister)
        vm.state.first { it.currentPath == "/" }

        vm.sendAction(FolderPickerAction.NavigateUpClicked)
        val state = vm.state.first()
        assertEquals("/", state.currentPath, "There's nowhere to ascend to from a root")
        assertEquals(null, state.parentPath)
    }

    @Test
    fun `ToggleHiddenClicked re-lists the current path with dotfiles and flips showHidden`() = runTest {
        val lister = FakeFolderLister(
            listings = mapOf(
                "/root" to FolderListing(
                    folders = listOf(FolderPickerEntry("Music", "/root/Music", 0, 0)),
                    fileCount = 1,
                ),
            ),
            hiddenListings = mapOf(
                "/root" to FolderListing(
                    folders = listOf(
                        FolderPickerEntry(".config", "/root/.config", 0, 0),
                        FolderPickerEntry("Music", "/root/Music", 0, 0),
                    ),
                    fileCount = 3,
                ),
            ),
        )
        val vm = newViewModel(defaultPath = "/root", lister = lister)
        val visible = vm.state.first { it.currentPath == "/root" }
        assertEquals(false, visible.showHidden)
        assertEquals(listOf("Music"), visible.entries.map { it.name })

        vm.sendAction(FolderPickerAction.ToggleHiddenClicked)
        val shown = vm.state.first { it.showHidden }
        assertEquals(listOf(".config", "Music"), shown.entries.map { it.name })
        assertEquals(3, shown.fileCount)

        // Toggling again hides the dotfiles once more.
        vm.sendAction(FolderPickerAction.ToggleHiddenClicked)
        val hiddenAgain = vm.state.first { !it.showHidden }
        assertEquals(listOf("Music"), hiddenAgain.entries.map { it.name })
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
    fun `descending into an unreadable directory lifts readable=false into state`() = runTest {
        val lister = FakeFolderLister(
            mapOf(
                "/storage/emulated" to FolderListing(
                    folders = emptyList(),
                    fileCount = 0,
                    parentPath = "/storage",
                    readable = false,
                ),
            ),
        )
        val vm = newViewModel(defaultPath = "/storage/emulated", lister = lister)

        val state = vm.state.first { it.currentPath == "/storage/emulated" }
        assertEquals(false, state.readable)
        assertEquals("/storage", state.parentPath, "The user must still be able to ascend out")
    }

    @Test
    fun `ReturnToDefaultClicked jumps back to the default path from an unreadable directory`() = runTest {
        val lister = FakeFolderLister(
            mapOf(
                "/storage/emulated/0" to FolderListing(
                    folders = listOf(FolderPickerEntry("Music", "/storage/emulated/0/Music", 0, 3)),
                    fileCount = 0,
                    parentPath = "/storage/emulated",
                ),
                "/storage/emulated" to FolderListing(
                    folders = emptyList(),
                    fileCount = 0,
                    parentPath = "/storage",
                    readable = false,
                ),
            ),
        )
        val vm = newViewModel(defaultPath = "/storage/emulated/0", lister = lister)
        vm.state.first { it.currentPath == "/storage/emulated/0" }

        // Ascend into the unreadable parent, then use the escape CTA to return to the default.
        vm.sendAction(FolderPickerAction.NavigateUpClicked)
        vm.state.first { it.currentPath == "/storage/emulated" && !it.readable }

        vm.sendAction(FolderPickerAction.ReturnToDefaultClicked)
        val state = vm.state.first { it.currentPath == "/storage/emulated/0" }
        assertTrue(state.readable)
        assertEquals(listOf("Music"), state.entries.map { it.name })
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
        private val hiddenListings: Map<String, FolderListing> = emptyMap(),
    ) : FolderLister {
        override fun list(path: String, showHidden: Boolean): FolderListing {
            val source = if (showHidden) hiddenListings else listings
            return source[path] ?: listings[path] ?: FolderListing(emptyList(), 0)
        }
    }
}
