package net.sigmabeta.chipbox.settings

import kotlinx.coroutines.flow.Flow

interface ChipboxSettingsManager {
    fun getBrandFont(): Flow<String?>
    fun setBrandFont(fontName: String)

    fun getPlainFont(): Flow<String?>
    fun setPlainFont(fontName: String)
}
