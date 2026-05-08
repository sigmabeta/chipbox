package net.sigmabeta.chipbox.settings

import kotlinx.coroutines.flow.Flow
import net.sigmabeta.sage.storage.common.Storage

class ChipboxSettingsManager(private val storage: Storage) {
    fun getBrandFont(): Flow<String?> = storage.savedStringFlow(KEY_BRAND_FONT)
    fun setBrandFont(fontName: String) = storage.saveString(KEY_BRAND_FONT, fontName)

    fun getPlainFont(): Flow<String?> = storage.savedStringFlow(KEY_PLAIN_FONT)
    fun setPlainFont(fontName: String) = storage.saveString(KEY_PLAIN_FONT, fontName)

    companion object {
        const val KEY_BRAND_FONT = "setting.font.brand"
        const val KEY_PLAIN_FONT = "setting.font.plain"
    }
}
