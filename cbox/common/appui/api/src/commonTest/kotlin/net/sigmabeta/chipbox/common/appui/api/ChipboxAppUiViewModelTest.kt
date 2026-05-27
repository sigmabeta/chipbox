package net.sigmabeta.chipbox.common.appui.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.sigmabeta.chipbox.settings.ThemeMode
import net.sigmabeta.chipbox.settings.fake.FakeChipboxSettingsManager
import net.sigmabeta.chipbox.ui.fonts.ChipboxFont
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ChipboxAppUiViewModel] is a thin wrapper that exposes three [ChipboxSettingsManager] streams
 * as `StateFlow`s for `ChipboxTheme` to consume. The interesting bit is the font streams —
 * each one maps the persisted string through `ChipboxFont.fromStorageValue(it, DEFAULT_X)`, so
 * a null / unknown name surfaces as the documented default.
 */
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
        val vm = ChipboxAppUiViewModel(settings)
        assertEquals(ThemeMode.LIGHT, vm.themeMode.first())
    }

    @Test
    fun `themeMode updates as the settings flow emits`() = runTest {
        val settings = FakeChipboxSettingsManager(initialThemeMode = ThemeMode.SYSTEM)
        val vm = ChipboxAppUiViewModel(settings)
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
        val vm = ChipboxAppUiViewModel(settings)
        assertEquals(ChipboxFont.DEFAULT_BRAND, vm.brandFont.first())
    }

    @Test
    fun `unknown brand font name also resolves to the default`() = runTest {
        // Same mapping path: if the persisted string doesn't match any enum entry,
        // fromStorageValue returns the supplied default rather than throwing.
        val settings = FakeChipboxSettingsManager(initialBrandFont = "a-font-that-was-removed")
        val vm = ChipboxAppUiViewModel(settings)
        assertEquals(ChipboxFont.DEFAULT_BRAND, vm.brandFont.first())
    }

    @Test
    fun `valid brand font name round-trips to the matching enum`() = runTest {
        // Pick any enum entry and feed its name; the VM should resolve to that entry.
        val target = ChipboxFont.entries.first { it != ChipboxFont.DEFAULT_BRAND }
        val settings = FakeChipboxSettingsManager(initialBrandFont = target.name)
        val vm = ChipboxAppUiViewModel(settings)
        assertEquals(target, vm.brandFont.first())
    }

    @Test
    fun `null plain font name resolves to DEFAULT_PLAIN`() = runTest {
        val settings = FakeChipboxSettingsManager(initialPlainFont = null)
        val vm = ChipboxAppUiViewModel(settings)
        assertEquals(ChipboxFont.DEFAULT_PLAIN, vm.plainFont.first())
    }

    @Test
    fun `font flows update as the underlying settings stream emits`() = runTest {
        val settings = FakeChipboxSettingsManager(initialBrandFont = null, initialPlainFont = null)
        val vm = ChipboxAppUiViewModel(settings)

        val targetBrand = ChipboxFont.entries.first { it != ChipboxFont.DEFAULT_BRAND }
        settings.setBrandFont(targetBrand.name)
        assertEquals(targetBrand, vm.brandFont.first { it == targetBrand })

        val targetPlain = ChipboxFont.entries.last { it != ChipboxFont.DEFAULT_PLAIN }
        settings.setPlainFont(targetPlain.name)
        assertEquals(targetPlain, vm.plainFont.first { it == targetPlain })
    }
}
