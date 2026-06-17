package net.sigmabeta.chipbox.settings

import kotlinx.coroutines.flow.Flow

interface ChipboxSettingsManager {
    fun getBrandFont(): Flow<String?>
    fun setBrandFont(fontName: String)

    fun getPlainFont(): Flow<String?>
    fun setPlainFont(fontName: String)

    fun getThemeMode(): Flow<ThemeMode>
    fun setThemeMode(mode: ThemeMode)

    fun getResamplerMode(): Flow<ResamplerMode>
    fun setResamplerMode(mode: ResamplerMode)

    fun getShuffleSkipsShortTracks(): Flow<Boolean>
    fun setShuffleSkipsShortTracks(value: Boolean)
}
