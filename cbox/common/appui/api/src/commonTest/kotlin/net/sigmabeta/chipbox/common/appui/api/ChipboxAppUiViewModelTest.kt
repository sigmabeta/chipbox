package net.sigmabeta.chipbox.common.appui.api

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.settings.ThemeMode
import net.sigmabeta.chipbox.settings.fake.FakeChipboxSettingsManager
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ChipboxAppUiViewModel] is a thin wrapper that exposes three [ChipboxSettingsManager] streams
 * as `StateFlow`s for `ChipboxTheme` to consume. The interesting bit is the font streams —
 * each one maps the persisted string through `ChipboxFont.fromStorageValue(it, DEFAULT_X)`, so
 * a null / unknown name surfaces as the documented default.
 */
private val testAppInfo = AppInfo(
    isDebug = false,
    versionName = "test",
    versionCode = 0,
    buildTimeMs = null,
    buildBranch = "test",
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChipboxAppUiViewModelTest {

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `themeMode seeds with the settings manager's initial value`() = runTest {
        val settings = FakeChipboxSettingsManager(initialThemeMode = ThemeMode.LIGHT)
        val vm = ChipboxAppUiViewModel(settings, testAppInfo, BluntHatchet())
        assertEquals(ThemeMode.LIGHT, vm.themeMode.first())
    }

    @Test
    fun `themeMode updates as the settings flow emits`() = runTest {
        val settings = FakeChipboxSettingsManager(initialThemeMode = ThemeMode.SYSTEM)
        val vm = ChipboxAppUiViewModel(settings, testAppInfo, BluntHatchet())
        assertEquals(ThemeMode.SYSTEM, vm.themeMode.first())

        settings.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, vm.themeMode.first { it == ThemeMode.DARK })
    }

    @Test
    fun `null brand font name resolves to the documented default`() = runTest {
        // The mapper is `ChipboxFont.fromStorageValue(it, DEFAULT_BRAND)`. A persisted null
        // (the cold-install case) should land on DEFAULT_BRAND, not crash or return some
        // arbitrary first-alphabetical entry.
        val settings = FakeChipboxSettingsManager(initialBrandFont = null)
        val vm = ChipboxAppUiViewModel(settings, testAppInfo, BluntHatchet())
        assertEquals(ChipboxFont.DEFAULT_BRAND, vm.brandFont.first())
    }

    @Test
    fun `unknown brand font name also resolves to the default`() = runTest {
        // Same mapping path: if the persisted string doesn't match any enum entry,
        // fromStorageValue returns the supplied default rather than throwing.
        val settings = FakeChipboxSettingsManager(initialBrandFont = "a-font-that-was-removed")
        val vm = ChipboxAppUiViewModel(settings, testAppInfo, BluntHatchet())
        assertEquals(ChipboxFont.DEFAULT_BRAND, vm.brandFont.first())
    }

    @Test
    fun `valid brand font name round-trips to the matching enum`() = runTest {
        // Pick any enum entry and feed its name; the VM should resolve to that entry.
        val target = ChipboxFont.entries.first { it != ChipboxFont.DEFAULT_BRAND }
        val settings = FakeChipboxSettingsManager(initialBrandFont = target.name)
        val vm = ChipboxAppUiViewModel(settings, testAppInfo, BluntHatchet())
        assertEquals(target, vm.brandFont.first())
    }

    @Test
    fun `null plain font name resolves to DEFAULT_PLAIN`() = runTest {
        val settings = FakeChipboxSettingsManager(initialPlainFont = null)
        val vm = ChipboxAppUiViewModel(settings, testAppInfo, BluntHatchet())
        assertEquals(ChipboxFont.DEFAULT_PLAIN, vm.plainFont.first())
    }

    @Test
    fun `isDebugBuild mirrors the injected AppInfo`() = runTest {
        val debug = AppInfo(isDebug = true, versionName = "x", versionCode = 1, buildTimeMs = null, buildBranch = "b")
        val release = testAppInfo.copy(isDebug = false)
        assertTrue(ChipboxAppUiViewModel(FakeChipboxSettingsManager(), debug, BluntHatchet()).isDebugBuild)
        assertEquals(
            false,
            ChipboxAppUiViewModel(FakeChipboxSettingsManager(), release, BluntHatchet()).isDebugBuild,
        )
    }

    @Test
    fun `currentRoute starts null and updates on setCurrentRoute`() = runTest {
        val vm = ChipboxAppUiViewModel(FakeChipboxSettingsManager(), testAppInfo, BluntHatchet())
        assertNull(vm.currentRoute.value)

        vm.setCurrentRoute("HomeTab/HomeTabRoot")
        assertEquals("HomeTab/HomeTabRoot", vm.currentRoute.value)

        vm.setCurrentRoute("LibraryTab/GameDetail:42")
        assertEquals("LibraryTab/GameDetail:42", vm.currentRoute.value)

        vm.setCurrentRoute(null)
        assertNull(vm.currentRoute.value)
    }

    @Test
    fun `handleEvent forwards events through effects`() = runTest {
        val vm = ChipboxAppUiViewModel(FakeChipboxSettingsManager(), testAppInfo, BluntHatchet())
        // Subscribe first so the SharedFlow has a collector when we emit — same pattern as
        // the screen sink/applyEffect collector wiring in production.
        val collected = async(start = CoroutineStart.UNDISPATCHED) { vm.effects.first() }
        vm.handleEvent(ChipboxEvent.RequestMiniPlayerVisibility(visible = false))
        val event = collected.await()
        assertTrue(event is ChipboxEvent.RequestMiniPlayerVisibility)
        assertEquals(false, event.visible)
    }

    @Test
    fun `handleEvent passes every event type through verbatim today`() = runTest {
        val vm = ChipboxAppUiViewModel(FakeChipboxSettingsManager(), testAppInfo, BluntHatchet())
        val ordered = listOf<ChipboxEvent>(
            ChipboxEvent.NavigateBack,
            ChipboxEvent.OpenUrl("https://example.com"),
            ChipboxEvent.RequestTopBarVisibility(visible = false),
            ChipboxEvent.RequestMiniPlayerVisibility(visible = true),
        )
        val collected = async(start = CoroutineStart.UNDISPATCHED) {
            vm.effects.take(ordered.size).toList()
        }
        ordered.forEach(vm::handleEvent)
        assertEquals(ordered, collected.await())
    }

    @Test
    fun `font flows update as the underlying settings stream emits`() = runTest {
        val settings = FakeChipboxSettingsManager(initialBrandFont = null, initialPlainFont = null)
        val vm = ChipboxAppUiViewModel(settings, testAppInfo, BluntHatchet())

        val targetBrand = ChipboxFont.entries.first { it != ChipboxFont.DEFAULT_BRAND }
        settings.setBrandFont(targetBrand.name)
        assertEquals(targetBrand, vm.brandFont.first { it == targetBrand })

        val targetPlain = ChipboxFont.entries.last { it != ChipboxFont.DEFAULT_PLAIN }
        settings.setPlainFont(targetPlain.name)
        assertEquals(targetPlain, vm.plainFont.first { it == targetPlain })
    }
}
