package net.sigmabeta.chipbox.debug.real

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.sage.storage.common.Storage

class RealDebugSettingsManager(private val storage: Storage) : DebugSettingsManager {
    override fun getShouldShowDebug(): Flow<Boolean> = storage
        .savedStringFlow(KEY_DEBUG_ENABLED)
        .map { it?.toBooleanStrictOrNull() ?: false }

    override fun setShouldShowDebug(value: Boolean) =
        storage.saveString(KEY_DEBUG_ENABLED, value.toString())

    companion object {
        const val KEY_DEBUG_ENABLED = "setting.debug.enabled"
    }
}
