package net.sigmabeta.chipbox.settings.real

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.chipbox.settings.ResamplerMode
import net.sigmabeta.chipbox.settings.ThemeMode
import net.sigmabeta.sage.storage.common.Storage

class RealChipboxSettingsManager(private val storage: Storage) : ChipboxSettingsManager {
    override fun getBrandFont(): Flow<String?> = storage.savedStringFlow(KEY_BRAND_FONT)
    override fun setBrandFont(fontName: String) = storage.saveString(KEY_BRAND_FONT, fontName)

    override fun getPlainFont(): Flow<String?> = storage.savedStringFlow(KEY_PLAIN_FONT)
    override fun setPlainFont(fontName: String) = storage.saveString(KEY_PLAIN_FONT, fontName)

    override fun getThemeMode(): Flow<ThemeMode> = storage
        .savedStringFlow(KEY_THEME_MODE)
        .map { ThemeMode.fromStorageValue(it) }

    override fun setThemeMode(mode: ThemeMode) = storage.saveString(KEY_THEME_MODE, mode.name)

    override fun getResamplerMode(): Flow<ResamplerMode> = storage
        .savedStringFlow(KEY_RESAMPLER_MODE)
        .map { ResamplerMode.fromStorageValue(it) }

    override fun setResamplerMode(mode: ResamplerMode) = storage.saveString(KEY_RESAMPLER_MODE, mode.name)

    companion object {
        const val KEY_BRAND_FONT = "setting.font.brand"
        const val KEY_PLAIN_FONT = "setting.font.plain"
        const val KEY_THEME_MODE = "setting.theme.mode"
        const val KEY_RESAMPLER_MODE = "setting.audio.resampler"
    }
}
