package net.sigmabeta.chipbox.debug

import kotlinx.coroutines.flow.Flow

interface DebugSettingsManager {
    fun getShouldShowDebug(): Flow<Boolean>
    fun setShouldShowDebug(value: Boolean)
}
