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
import net.sigmabeta.chipbox.contentsource.LibraryLocationInfo
import net.sigmabeta.chipbox.contentsource.fake.FakeLibrarySource
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Add this folder" refuses a location that overlaps the configured roots. Failing here (at the
 * point of the mistake) means the user sees why instead of discovering a failed scan later, and
 * nothing is committed or scanned.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FolderPickerOverlapTest {

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
    fun `adding a folder nested inside an existing root is refused with a message`() = runTest {
        val source = FakeLibrarySource(
            initial = listOf(LibraryLocationInfo("/music", "Music")),
        )
        val scanner = CountingScanner(dispatcher)
        val vm = newViewModel(defaultPath = "/music/SNES", source = source, scanner = scanner)
        vm.state.first { it.currentPath == "/music/SNES" }

        val event = collectAndDispatch(vm, FolderPickerAction.AddThisFolderClicked)

        assertTrue(event is ChipboxEvent.ShowSnackbar, "the refusal must be visible: $event")
        // The message is a resource, not prose: the id plus the existing root's display name.
        assertEquals("${ChipboxStringId.FOLDER_PICKER_SNACKBAR_OVERLAP}:Music", event.message)
        assertTrue(source.addedLocations.isEmpty(), "a refused location is never committed")
        assertEquals(0, scanner.startCount, "a refused location does not start a scan")
    }

    @Test
    fun `adding the same folder twice is refused with a message`() = runTest {
        val source = FakeLibrarySource(
            initial = listOf(LibraryLocationInfo("/music", "Music")),
        )
        val scanner = CountingScanner(dispatcher)
        val vm = newViewModel(defaultPath = "/music", source = source, scanner = scanner)
        vm.state.first { it.currentPath == "/music" }

        val event = collectAndDispatch(vm, FolderPickerAction.AddThisFolderClicked)

        assertTrue(event is ChipboxEvent.ShowSnackbar, "the duplicate must be visible: $event")
        assertTrue(source.addedLocations.isEmpty())
        assertEquals(0, scanner.startCount)
    }

    @Test
    fun `adding a folder that contains an existing root is refused`() = runTest {
        val source = FakeLibrarySource(
            initial = listOf(LibraryLocationInfo("/music/SNES", "SNES")),
        )
        val scanner = CountingScanner(dispatcher)
        val vm = newViewModel(defaultPath = "/music", source = source, scanner = scanner)
        vm.state.first { it.currentPath == "/music" }

        val event = collectAndDispatch(vm, FolderPickerAction.AddThisFolderClicked)

        assertTrue(event is ChipboxEvent.ShowSnackbar, "the refusal must be visible: $event")
        assertTrue(source.addedLocations.isEmpty())
        assertEquals(0, scanner.startCount)
    }

    // ---- helpers ----

    private fun newViewModel(
        defaultPath: String,
        source: FakeLibrarySource,
        scanner: CountingScanner,
    ) = FolderPickerViewModel(
        defaultPath = defaultPath,
        folderLister = EmptyFolderLister,
        storageVolumeProvider = { emptyList() },
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
        override fun getStringOneArg(string: SageStringId, arg: String): String = "$string:$arg"
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String =
            string.toString()
    }

    private object EmptyFolderLister : FolderLister {
        override fun list(path: String, showHidden: Boolean): FolderListing =
            FolderListing(emptyList(), 0)
    }
}
