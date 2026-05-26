package net.sigmabeta.chipbox.debug.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sigmabeta.chipbox.debug.DebugSettingsManager

/**
 * Test [DebugSettingsManager] backed by a [MutableStateFlow]. [setShouldShowDebug] both writes
 * the flow AND records the call so assertions can verify a ViewModel toggled the right value.
 */
class FakeDebugSettingsManager(
    initialShouldShowDebug: Boolean = false,
) : DebugSettingsManager {

    private val sink = MutableStateFlow(initialShouldShowDebug)

    val setShouldShowDebugCalls: MutableList<Boolean> = mutableListOf()

    override fun getShouldShowDebug(): Flow<Boolean> = sink.asStateFlow()
    override fun setShouldShowDebug(value: Boolean) {
        setShouldShowDebugCalls += value
        sink.value = value
    }
}
