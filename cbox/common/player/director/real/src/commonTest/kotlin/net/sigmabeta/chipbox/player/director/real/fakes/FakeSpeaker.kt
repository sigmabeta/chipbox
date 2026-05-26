package net.sigmabeta.chipbox.player.director.real.fakes

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.player.speaker.SpeakerDebugInfo
import net.sigmabeta.chipbox.player.speaker.SpeakerEvent

/**
 * Test [Speaker] — same shape as [FakeGenerator]: events pushed by tests via [emit], every other
 * method records the call. [currentPositionMsValue] is settable so tests can verify the Director
 * stamps the right position on emitted [ChipboxPlaybackState]s.
 */
class FakeSpeaker : Speaker {

    private val eventSink = MutableSharedFlow<SpeakerEvent>(
        replay = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 16,
    )
    private val debugInfo = MutableStateFlow(SpeakerDebugInfo())

    var currentPositionMsValue: Long = 0L

    var playCalls: Int = 0
    var pauseCalls: Int = 0
    var stopCalls: Int = 0
    var seekCalls: Int = 0
    val switchToCalls = mutableListOf<Long>()
    var releaseCalls: Int = 0
    val setDuckedCalls = mutableListOf<Boolean>()
    val setVolumeCalls = mutableListOf<Double>()

    /** Push [event] into the events flow that the Director subscribes to in its `init`. */
    suspend fun emit(event: SpeakerEvent) = eventSink.emit(event)

    override fun events() = eventSink.asSharedFlow()
    override fun debugInfo() = debugInfo.asStateFlow()
    override fun currentPositionMs(): Long = currentPositionMsValue
    override fun release() { releaseCalls++ }
    override fun play() { playCalls++ }
    override suspend fun pause() { pauseCalls++ }
    override suspend fun stop() { stopCalls++ }
    override suspend fun seek() { seekCalls++ }
    override suspend fun switchTo(trackId: Long) { switchToCalls += trackId }
    override fun setDucked(ducked: Boolean) { setDuckedCalls += ducked }
    override fun setVolume(scale: Double) { setVolumeCalls += scale }
    override fun setVolumeModification(key: String, scale: Double) = Unit
    override fun clearVolumeModification(key: String) = Unit
}
