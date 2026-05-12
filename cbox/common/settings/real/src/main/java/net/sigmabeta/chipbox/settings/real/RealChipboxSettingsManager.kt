package net.sigmabeta.chipbox.settings.real

import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.sage.storage.common.Storage

class RealChipboxSettingsManager(private val storage: Storage) : ChipboxSettingsManager {
    override fun getBrandFont(): Flow<String?> = storage.savedStringFlow(KEY_BRAND_FONT)
    override fun setBrandFont(fontName: String) = storage.saveString(KEY_BRAND_FONT, fontName)

    override fun getPlainFont(): Flow<String?> = storage.savedStringFlow(KEY_PLAIN_FONT)
    override fun setPlainFont(fontName: String) = storage.saveString(KEY_PLAIN_FONT, fontName)

    companion object {
        const val KEY_BRAND_FONT = "setting.font.brand"
        const val KEY_PLAIN_FONT = "setting.font.plain"
    }
}
