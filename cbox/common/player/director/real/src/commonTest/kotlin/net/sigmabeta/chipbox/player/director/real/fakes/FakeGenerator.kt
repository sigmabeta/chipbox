package net.sigmabeta.chipbox.player.director.real.fakes

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.generator.GeneratorDebugInfo
import net.sigmabeta.chipbox.player.generator.GeneratorEvent

/**
 * Test [Generator] that lets tests push [GeneratorEvent]s through [emit] and inspect what the
 * Director called on it. No real production loop — exactly what's needed to drive the Director's
 * reducer in isolation.
 *
 * Event buffer matches the production [net.sigmabeta.chipbox.player.generator.BaseGenerator]
 * shape (replay=0, suspend on overflow, 10 extra capacity) so a test that floods events without
 * waiting for a collector doesn't deadlock on the very first emit.
 */
class FakeGenerator : Generator {

    private val eventSink = MutableSharedFlow<GeneratorEvent>(
        replay = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 16,
    )
    private val debugInfo = MutableStateFlow(GeneratorDebugInfo())

    val startTrackCalls = mutableListOf<Long>()
    var playCalls: Int = 0
    var pauseCalls: Int = 0
    var stopCalls: Int = 0
    val seekCalls = mutableListOf<Long>()
    var releaseCalls: Int = 0

    /** Push [event] into the events flow that the Director subscribes to in its `init`. */
    suspend fun emit(event: GeneratorEvent) = eventSink.emit(event)

    override fun events() = eventSink.asSharedFlow()
    override fun debugInfo() = debugInfo.asStateFlow()
    override fun release() { releaseCalls++ }
    override suspend fun startTrack(trackId: Long) { startTrackCalls += trackId }
    override fun play() { playCalls++ }
    override fun pause() { pauseCalls++ }
    override suspend fun stop() { stopCalls++ }
    override suspend fun seek(positionMs: Long) { seekCalls += positionMs }
}
