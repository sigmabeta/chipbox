package net.sigmabeta.chipbox.settings.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.chipbox.settings.ResamplerMode
import net.sigmabeta.chipbox.settings.ThemeMode

/**
 * Test [ChipboxSettingsManager] backed by a [MutableStateFlow] per setting. Each getX() returns
 * the live flow so subscribers see every change; each setX() writes into the flow AND records
 * the call so assertions can verify the ViewModel under test dispatched the right thing.
 *
 * Seed values match production's `RealChipboxSettingsManager` defaults — [ThemeMode.DEFAULT] for
 * the theme, `null` for the font names (the consumer interprets null as "use the documented
 * default font").
 */
class FakeChipboxSettingsManager(
    initialThemeMode: ThemeMode = ThemeMode.DEFAULT,
    initialBrandFont: String? = null,
    initialPlainFont: String? = null,
    initialResamplerMode: ResamplerMode = ResamplerMode.DEFAULT,
    initialShuffleSkipsShortTracks: Boolean = true,
) : ChipboxSettingsManager {

    private val themeMode = MutableStateFlow(initialThemeMode)
    private val brandFont = MutableStateFlow(initialBrandFont)
    private val plainFont = MutableStateFlow(initialPlainFont)
    private val resamplerMode = MutableStateFlow(initialResamplerMode)
    private val shuffleSkipsShortTracks = MutableStateFlow(initialShuffleSkipsShortTracks)

    val setBrandFontCalls: MutableList<String> = mutableListOf()
    val setPlainFontCalls: MutableList<String> = mutableListOf()
    val setThemeModeCalls: MutableList<ThemeMode> = mutableListOf()
    val setResamplerModeCalls: MutableList<ResamplerMode> = mutableListOf()
    val setShuffleSkipsShortTracksCalls: MutableList<Boolean> = mutableListOf()

    override fun getThemeMode(): Flow<ThemeMode> = themeMode.asStateFlow()
    override fun setThemeMode(mode: ThemeMode) {
        setThemeModeCalls += mode
        themeMode.value = mode
    }

    override fun getResamplerMode(): Flow<ResamplerMode> = resamplerMode.asStateFlow()
    override fun setResamplerMode(mode: ResamplerMode) {
        setResamplerModeCalls += mode
        resamplerMode.value = mode
    }

    override fun getBrandFont(): Flow<String?> = brandFont.asStateFlow()
    override fun setBrandFont(fontName: String) {
        setBrandFontCalls += fontName
        brandFont.value = fontName
    }

    override fun getPlainFont(): Flow<String?> = plainFont.asStateFlow()
    override fun setPlainFont(fontName: String) {
        setPlainFontCalls += fontName
        plainFont.value = fontName
    }

    override fun getShuffleSkipsShortTracks(): Flow<Boolean> = shuffleSkipsShortTracks.asStateFlow()
    override fun setShuffleSkipsShortTracks(value: Boolean) {
        setShuffleSkipsShortTracksCalls += value
        shuffleSkipsShortTracks.value = value
    }
}
