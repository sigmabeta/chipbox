package net.sigmabeta.chipbox.debuginfo.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sigmabeta.chipbox.debuginfo.DebugInfoManager
import net.sigmabeta.chipbox.debuginfo.PlaybackDebugInfo

/**
 * Test [DebugInfoManager] backed by a [MutableStateFlow]. Tests push snapshots through
 * [emit] and the screen's view-model — which subscribes to [debugInfo] in its `init` — folds
 * them into its state. Seed value is the empty [PlaybackDebugInfo], matching production
 * `RealDebugInfoManager`'s "no measurements yet" baseline.
 */
class FakeDebugInfoManager : DebugInfoManager {
    private val sink = MutableStateFlow(PlaybackDebugInfo())
    override fun debugInfo(): StateFlow<PlaybackDebugInfo> = sink.asStateFlow()
    fun emit(info: PlaybackDebugInfo) { sink.value = info }
}
