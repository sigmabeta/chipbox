package net.sigmabeta.chipbox.features.settings

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
import net.sigmabeta.chipbox.debug.fake.FakeDebugSettingsManager
import net.sigmabeta.chipbox.history.fake.FakePlaybackHistoryRepository
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.chipbox.settings.fake.FakeChipboxSettingsManager
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.components.NameCaptionListModel
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
 * Host-policy rendering: the same [SettingsState] list is used on desktop/Android and in the
 * browser, so the library-administration and debug surfaces must be suppressible without changing
 * the default (desktop) output.
 */
class SettingsCapabilitiesTest {

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `server-managed library hides admin rows and keeps clear playback history`() {
        val actions = clickActions(
            SettingsState(canManageLibrary = false, hasLibraryFolders = true),
        )

        assertFalse(SettingsAction.AddFolderClicked in actions)
        assertFalse(SettingsAction.ManageLibraryClicked in actions)
        assertFalse(SettingsAction.RescanLibraryClicked in actions)
        assertFalse(SettingsAction.RescanStatusClicked in actions)
        assertFalse(SettingsAction.ClearLibraryClicked in actions)
        assertTrue(SettingsAction.ClearPlaybackHistoryClicked in actions)
    }

    @Test
    fun `server-managed library hides the rescan status row even while a scan reports loading`() {
        val actions = clickActions(
            SettingsState(canManageLibrary = false, rescanStatus = net.sigmabeta.sage.appcomm.LCE.Loading("rescan")),
        )

        assertFalse(SettingsAction.RescanStatusClicked in actions)
    }

    @Test
    fun `diagnostics row exposes the error log when the debug section is unavailable`() {
        val items = SettingsState(
            showDebugSection = false,
            showErrorLog = true,
            // The persisted five-tap flag must not be able to reveal the rest of the debug surface.
            shouldShowDebug = true,
        ).toListItems(stubStringProvider())

        val actions = items.filterIsInstance<NameCaptionListModel>().map { it.clickAction }
        assertTrue(SettingsAction.ErrorLogClicked in actions)
        assertFalse(SettingsAction.ImportSnapshotClicked in actions)
        assertFalse(SettingsAction.PlaybackStatusClicked in actions)
        assertFalse(SettingsAction.CrashLogClicked in actions)
        assertFalse(SettingsAction.ComponentLibraryClicked in actions)
    }

    @Test
    fun `no error-log entry point when the host withholds it`() {
        val actions = clickActions(SettingsState(showDebugSection = false, showErrorLog = false))

        assertFalse(SettingsAction.ErrorLogClicked in actions)
    }

    @Test
    fun `the debug section honours showErrorLog independently`() {
        val actions = clickActions(
            SettingsState(showDebugSection = true, showErrorLog = false, shouldShowDebug = true),
        )

        assertFalse(SettingsAction.ErrorLogClicked in actions, "showErrorLog=false must drop the row from the debug section")
        assertTrue(SettingsAction.PlaybackStatusClicked in actions, "the rest of the debug section still renders")
    }

    @Test
    fun `debug section is still absent without the persisted toggle`() {
        val actions = clickActions(SettingsState(showDebugSection = true, shouldShowDebug = false))

        assertFalse(SettingsAction.ErrorLogClicked in actions)
        assertFalse(SettingsAction.PlaybackStatusClicked in actions)
    }

    @Test
    fun `view model seeds the state flags from capabilities`() = runTest {
        val vm = newViewModel(
            capabilities = AppCapabilities(
                canManageLibrary = false,
                showDebugSection = false,
                showErrorLog = true,
            ),
        )

        val state = vm.state.first()
        assertFalse(state.canManageLibrary)
        assertFalse(state.showDebugSection)
        assertTrue(state.showErrorLog)
    }

    @Test
    fun `server-managed library rejects every admin action at the view model`() = runTest {
        val library = FakeLibrarySource()
        val scanner = CountingScanner()
        val repository = FakeRepository(emptyMap())
        val vm = newViewModel(
            capabilities = AppCapabilities(canManageLibrary = false),
            librarySource = library,
            scanner = scanner,
            repository = repository,
        )

        val adminActions = listOf(
            SettingsAction.AddFolderClicked,
            SettingsAction.ManageLibraryClicked,
            SettingsAction.RescanLibraryClicked,
            SettingsAction.RescanStatusClicked,
            SettingsAction.ClearLibraryClicked,
            SettingsAction.FolderPicked("content://tree/Music"),
        )
        adminActions.forEach { action ->
            assertNull(collectOrNull(vm, action), "expected no event for $action")
        }

        assertTrue(library.addedLocations.isEmpty())
        assertEquals(0, scanner.startCount)
        assertFalse(
            vm.state.value.clearLibraryStatus is net.sigmabeta.sage.appcomm.LCE.Loading,
            "clear-library must not start",
        )
    }

    @Test
    fun `server-managed library keeps clear playback history functional`() = runTest {
        val history = FakePlaybackHistoryRepository()
        val vm = newViewModel(
            capabilities = AppCapabilities(canManageLibrary = false),
            playbackHistoryRepository = history,
        )

        val event = collectAndDispatch(vm, SettingsAction.ClearPlaybackHistoryClicked)

        assertTrue(event is ChipboxEvent.ShowSnackbar)
        assertEquals(1, history.clearCalls)
    }

    @Test
    fun `five debug taps do not persist the debug toggle when the host hides debug`() = runTest {
        val debug = FakeDebugSettingsManager()
        val vm = newViewModel(
            capabilities = AppCapabilities(showDebugSection = false),
            debug = debug,
        )

        repeat(5) { vm.sendAction(SettingsAction.BuildDateClicked) }

        assertTrue(debug.setShouldShowDebugCalls.isEmpty())
    }

    private fun newViewModel(
        capabilities: AppCapabilities = AppCapabilities(),
        settings: FakeChipboxSettingsManager = FakeChipboxSettingsManager(),
        debug: FakeDebugSettingsManager = FakeDebugSettingsManager(),
        repository: Repository = FakeRepository(emptyMap()),
        playbackHistoryRepository: FakePlaybackHistoryRepository = FakePlaybackHistoryRepository(),
        scanner: CountingScanner = CountingScanner(),
        librarySource: LibrarySource = FakeLibrarySource(),
    ) = SettingsViewModel(
        capabilities = capabilities,
        settingsManager = settings,
        debugSettingsManager = debug,
        repository = repository,
        playbackHistoryRepository = playbackHistoryRepository,
        scanner = scanner,
        librarySource = librarySource,
        appInfo = AppInfo(
            isDebug = false,
            versionName = "test",
            versionCode = 1,
            buildTimeMs = null,
            buildBranch = "test",
        ),
        stringProvider = stubStringProvider(),
        hatchet = BluntHatchet(),
    )

    private suspend fun CoroutineScope.collectAndDispatch(
        vm: SettingsViewModel,
        action: SageAction,
    ): ChipboxEvent = async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
        .also { vm.sendAction(action) }
        .await()

    private suspend fun collectOrNull(vm: SettingsViewModel, action: SageAction): ChipboxEvent? =
        withTimeoutOrNull(50) {
            coroutineScope {
                async(start = CoroutineStart.UNDISPATCHED) { vm.events.first() }
                    .also { vm.sendAction(action) }
                    .await()
            }
        }

    private fun clickActions(state: SettingsState): List<Any?> = state
        .toListItems(stubStringProvider())
        .filterIsInstance<NameCaptionListModel>()
        .map { it.clickAction }

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }
}
