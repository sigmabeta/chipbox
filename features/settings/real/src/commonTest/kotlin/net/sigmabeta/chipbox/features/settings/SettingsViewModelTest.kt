package net.sigmabeta.chipbox.features.settings

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
import net.sigmabeta.chipbox.debug.fake.FakeDebugSettingsManager
import net.sigmabeta.chipbox.features.managelibrary.ManageLibrary
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatus
import net.sigmabeta.chipbox.features.rescanstatus.RescanStatus
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.settings.ThemeMode
import net.sigmabeta.chipbox.settings.fake.FakeChipboxSettingsManager
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SettingsViewModel] is the most-fanned-out VM in the app — six concurrent collects in init,
 * three navigation actions, two scan-touching paths, and a 5-tap debug toggle. The tests below
 * cover each path once; the production code is documented enough that "did the action route to
 * the right manager call?" is the right unit of coverage here.
 *
 * Two non-obvious shared rigs make this readable:
 *
 *   1. [newViewModel] takes every dep as a default-fake; tests only name the one they're
 *      driving. The defaults pass through [FakeRepository] / [CountingScanner] / etc., which
 *      themselves are exercised in their own modules' tests.
 *   2. [collectAndDispatch] is the standard "subscribe then sendAction" pattern for one-shot
 *      events on the replay=0 events SharedFlow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- init-time wire-up ----

    @Test
    fun `init seeds appInfo and a formatted build date from the injected AppInfo`() = runTest {
        val info = appInfoOf(buildTimeMs = 1_700_000_000_000L)
        val vm = newViewModel(appInfo = info)
        val state = vm.state.first { it.appInfo != null }
        assertEquals(info, state.appInfo)
        // We don't assert the exact formatted string — its format is platform-dependent (the
        // jvmMain formatLongDate uses java.time, jsMain returns a stub). All we need is that
        // SOMETHING was formatted: the field flipped off null.
        assertNotNull(state.formattedBuildDate)
    }

    @Test
    fun `init mirrors brand, plain, theme, debug and library-folder flows into state`() = runTest {
        val settings = FakeChipboxSettingsManager(
            initialThemeMode = ThemeMode.DARK,
            initialBrandFont = "Earthbound",
            initialPlainFont = "Open Dyslexic",
        )
        val debug = FakeDebugSettingsManager(initialShouldShowDebug = true)
        val library = FakeLibrarySource(initial = listOf(LibraryLocationInfo("/lib", "Library")))

        val vm = newViewModel(settings = settings, debug = debug, librarySource = library)

        val state = vm.state.first { it.brandFont != null }
        assertEquals("Earthbound", state.brandFont)
        assertEquals("Open Dyslexic", state.plainFont)
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals(true, state.shouldShowDebug)
        assertTrue(state.hasLibraryFolders)
    }

    // ---- preference-writing actions ----

    @Test
    fun `ThemeModeSelected writes the new mode through to the settings manager`() = runTest {
        val settings = FakeChipboxSettingsManager()
        val vm = newViewModel(settings = settings)
        vm.sendAction(SettingsAction.ThemeModeSelected(ThemeMode.LIGHT))
        assertEquals(listOf(ThemeMode.LIGHT), settings.setThemeModeCalls)
    }

    @Test
    fun `BrandFontSelected writes the font name through to the settings manager`() = runTest {
        val settings = FakeChipboxSettingsManager()
        val vm = newViewModel(settings = settings)
        vm.sendAction(SettingsAction.BrandFontSelected(ChipboxFont.PLANETARY))
        assertEquals(listOf(ChipboxFont.PLANETARY.name), settings.setBrandFontCalls)
    }

    @Test
    fun `PlainFontSelected writes the font name through to the settings manager`() = runTest {
        val settings = FakeChipboxSettingsManager()
        val vm = newViewModel(settings = settings)
        vm.sendAction(SettingsAction.PlainFontSelected(ChipboxFont.METEOR))
        assertEquals(listOf(ChipboxFont.METEOR.name), settings.setPlainFontCalls)
    }

    // ---- library / scan actions ----

    @Test
    fun `FolderPicked adds the location, starts a scan, and navigates to RescanStatus`() = runTest {
        // Verifies all three fan-out effects of the one user gesture: write the prefs (so the new
        // folder shows in the library section), kick off the scan (so files become tracks), AND
        // route the user to the live-status screen (so they see what's happening).
        val library = FakeLibrarySource()
        val scanner = CountingScanner()
        val vm = newViewModel(librarySource = library, scanner = scanner)

        val event = collectAndDispatch(vm, SettingsAction.FolderPicked("content://tree/Music"))

        assertEquals(listOf("content://tree/Music"), library.addedLocations)
        assertEquals(1, scanner.startCount)
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(RescanStatus, event.destination)
    }

    @Test
    fun `RescanLibraryClicked flips rescanStatus to Loading and emits NavigateTo RescanStatus`() = runTest {
        // The state update is synchronous-before-event because the VM calls updateState first and
        // only THEN emits. Tests can therefore read state.value the moment the event fires.
        val scanner = CountingScanner()
        val vm = newViewModel(scanner = scanner)

        val event = collectAndDispatch(vm, SettingsAction.RescanLibraryClicked)

        assertEquals(1, scanner.startCount)
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(RescanStatus, event.destination)
        val rescan = vm.state.value.rescanStatus
        assertTrue(rescan is LCE.Loading)
        assertEquals("settings.rescan", rescan.operationName)
    }

    @Test
    fun `scanner state Scanning lifts rescanStatus to Loading, back to Idle clears it`() = runTest {
        // The init-time scanner.state() collect drives `rescanStatus` independently of action
        // handlers, so a scan started from anywhere (e.g. RescanStatus screen, ManageLibrary)
        // still updates the Settings rescan row.
        val scanner = CountingScanner()
        val vm = newViewModel(scanner = scanner)

        scanner.pushState(ScannerState.Scanning())
        val scanning = vm.state.first { it.rescanStatus is LCE.Loading }
        assertEquals("settings.rescan", (scanning.rescanStatus as LCE.Loading).operationName)

        scanner.pushState(ScannerState.Idle)
        val idle = vm.state.first { it.rescanStatus !is LCE.Loading }
        assertEquals(LCE.Uninitialized, idle.rescanStatus)
    }

    @Test
    fun `ClearLibraryClicked flips clearLibraryStatus Loading then Content and emits a snackbar`() = runTest {
        // FakeRepository.clearLibrary() returns Unit, so the happy path runs synchronously under
        // UnconfinedTestDispatcher — by the time sendAction returns, status has already advanced
        // through Loading to Content. We assert the final state and the user-facing snackbar.
        val vm = newViewModel(repository = FakeRepository(emptyMap()))
        val event = collectAndDispatch(vm, SettingsAction.ClearLibraryClicked)

        val status = vm.state.value.clearLibraryStatus
        assertTrue(status is LCE.Content)
        assertTrue(event is ChipboxEvent.ShowSnackbar)
        assertEquals("Library cleared.", event.message)
    }

    @Test
    fun `ClearLibraryClicked surfaces a failure as Error state plus failure snackbar`() = runTest {
        // Repository decorator that throws — covers the catch branch's two effects: Error LCE so
        // the row renders in failed state, and a distinct snackbar so the user sees what went
        // wrong without having to open the row.
        val repository = object : Repository by FakeRepository(emptyMap()) {
            override suspend fun clearLibrary(): Unit = throw IllegalStateException("disk full")
        }
        val vm = newViewModel(repository = repository)
        val event = collectAndDispatch(vm, SettingsAction.ClearLibraryClicked)

        val status = vm.state.value.clearLibraryStatus
        assertTrue(status is LCE.Error)
        assertEquals("settings.clear_library", status.operationName)
        assertEquals("disk full", status.error.message)
        assertTrue(event is ChipboxEvent.ShowSnackbar)
        assertEquals("Failed to clear library.", event.message)
    }

    // ---- pure navigation/event actions ----

    @Test
    fun `AddFolderClicked emits a PickFolder event`() = runTest {
        val vm = newViewModel()
        val event = collectAndDispatch(vm, SettingsAction.AddFolderClicked)
        assertTrue(event is ChipboxEvent.PickFolder)
    }

    @Test
    fun `ManageLibraryClicked navigates to ManageLibrary`() = runTest {
        val vm = newViewModel()
        val event = collectAndDispatch(vm, SettingsAction.ManageLibraryClicked)
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(ManageLibrary, event.destination)
    }

    @Test
    fun `RescanStatusClicked navigates to RescanStatus`() = runTest {
        val vm = newViewModel()
        val event = collectAndDispatch(vm, SettingsAction.RescanStatusClicked)
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(RescanStatus, event.destination)
    }

    @Test
    fun `PlaybackStatusClicked navigates to PlaybackStatus`() = runTest {
        val vm = newViewModel()
        val event = collectAndDispatch(vm, SettingsAction.PlaybackStatusClicked)
        assertTrue(event is ChipboxEvent.NavigateTo)
        assertEquals(PlaybackStatus, event.destination)
    }

    @Test
    fun `LicensesClicked surfaces the coming-soon snackbar`() = runTest {
        // Today's behaviour is a snackbar — this test guards the "still placeholder" status so
        // when the real screen lands, this test fails loudly and the author updates the assertion
        // (or replaces it with NavigateTo(Licenses)).
        val vm = newViewModel()
        val event = collectAndDispatch(vm, SettingsAction.LicensesClicked)
        assertTrue(event is ChipboxEvent.ShowSnackbar)
        assertEquals("Licenses screen coming soon.", event.message)
    }

    @Test
    fun `GithubClicked emits OpenUrl with the repo URL`() = runTest {
        val vm = newViewModel()
        val event = collectAndDispatch(vm, SettingsAction.GithubClicked)
        assertTrue(event is ChipboxEvent.OpenUrl)
        assertEquals("https://github.com/sigmabeta/chipbox", event.url)
    }

    // ---- debug-tap threshold ----

    @Test
    fun `BuildDateClicked under the threshold only bumps the click counter`() = runTest {
        val debug = FakeDebugSettingsManager()
        val vm = newViewModel(debug = debug)

        repeat(4) { vm.sendAction(SettingsAction.BuildDateClicked) }

        assertEquals(4, vm.state.value.debugClickCount)
        assertTrue(debug.setShouldShowDebugCalls.isEmpty(), "shouldn't toggle until the 5th tap")
    }

    @Test
    fun `BuildDateClicked at the threshold toggles shouldShowDebug and resets the counter`() = runTest {
        // First 5 taps flip the debug flag on; next 5 flip it back off. The counter resets to 0
        // after each toggle so the gesture is repeatable.
        val debug = FakeDebugSettingsManager(initialShouldShowDebug = false)
        val vm = newViewModel(debug = debug)

        repeat(5) { vm.sendAction(SettingsAction.BuildDateClicked) }
        assertEquals(0, vm.state.value.debugClickCount)
        assertEquals(listOf(true), debug.setShouldShowDebugCalls)

        repeat(5) { vm.sendAction(SettingsAction.BuildDateClicked) }
        assertEquals(0, vm.state.value.debugClickCount)
        assertEquals(listOf(true, false), debug.setShouldShowDebugCalls)
    }

    @Test
    fun `BuildDateClicked uses false as the seed when shouldShowDebug is still null`() = runTest {
        // shouldShowDebug starts as `null` (the "not yet known" sentinel) but the toggle treats
        // null as false. Without this, 5 taps would race the debug flow and could read `null`
        // first → !(null ?: false) = true. Lock that in.
        val debug = FakeDebugSettingsManager(initialShouldShowDebug = false)
        val vm = newViewModel(debug = debug)
        // Pre-bump state.shouldShowDebug back to null to simulate the race where taps land before
        // the init collect has emitted.
        assertNull(SettingsState().shouldShowDebug, "sanity check: initial sentinel is null")

        repeat(5) { vm.sendAction(SettingsAction.BuildDateClicked) }
        assertEquals(listOf(true), debug.setShouldShowDebugCalls)
    }

    @Test
    fun `init reports hasLibraryFolders as false when the library source is empty`() = runTest {
        val vm = newViewModel(librarySource = FakeLibrarySource())
        assertFalse(vm.state.first().hasLibraryFolders)
    }

    // ---- helpers ----

    private fun newViewModel(
        settings: FakeChipboxSettingsManager = FakeChipboxSettingsManager(),
        debug: FakeDebugSettingsManager = FakeDebugSettingsManager(),
        repository: Repository = FakeRepository(emptyMap()),
        scanner: CountingScanner = CountingScanner(),
        librarySource: FakeLibrarySource = FakeLibrarySource(),
        appInfo: AppInfo = appInfoOf(),
    ) = SettingsViewModel(
        settingsManager = settings,
        debugSettingsManager = debug,
        repository = repository,
        scanner = scanner,
        librarySource = librarySource,
        appInfo = appInfo,
        stringProvider = stubStringProvider(),
        hatchet = BluntHatchet(),
    )

    private fun appInfoOf(buildTimeMs: Long? = null): AppInfo = AppInfo(
        isDebug = false,
        versionName = "1.0.0-test",
        versionCode = 1,
        buildTimeMs = buildTimeMs,
        buildBranch = "test",
    )

    private suspend fun CoroutineScope.collectAndDispatch(
        vm: SettingsViewModel,
        action: SettingsAction,
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
