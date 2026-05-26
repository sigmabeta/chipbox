package net.sigmabeta.chipbox.player.director.fake

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerErrorEvent
import net.sigmabeta.chipbox.player.director.PlayerState

/**
 * Test-only [Director] stub. Subscribers of [metadataState] / [playbackState] / [sessionState] /
 * [errorEvents] see whatever the test pushes through [emitMetadata] / [emitPlayback] / the raw
 * sinks; the call-handling methods just bump a counter (or append to a list) so assertions can
 * verify the ViewModel under test dispatched the right thing.
 *
 * `replay = 1` on the metadata / playback / session sinks mirrors production `RealDirector`'s
 * replay so a freshly-subscribed collector immediately sees the seeded "nothing playing"
 * baseline rather than waiting indefinitely for the first emission.
 */
class FakeDirector : Director {

    private val metadataSink = MutableSharedFlow<Track?>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    private val playbackSink = MutableSharedFlow<ChipboxPlaybackState>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    private val sessionSink = MutableSharedFlow<Session?>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    private val errorSink = MutableSharedFlow<PlayerErrorEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        // Match RealDirector's init: seed the replay buffers so an early subscriber sees a
        // meaningful "nothing playing" value rather than waiting indefinitely.
        metadataSink.tryEmit(null)
        playbackSink.tryEmit(
            ChipboxPlaybackState(
                state = PlayerState.IDLE,
                position = 0L,
                generatorProducedMs = 0L,
                playbackSpeed = 1.0f,
                skipForwardAllowed = false,
                errorMessage = null,
            )
        )
        sessionSink.tryEmit(null)
    }

    suspend fun emitMetadata(track: Track?) = metadataSink.emit(track)
    suspend fun emitPlayback(state: ChipboxPlaybackState) = playbackSink.emit(state)

    /** Convenience: emit a playback state with only [state] varied; other fields default. */
    suspend fun emitPlayback(state: PlayerState) = emitPlayback(
        ChipboxPlaybackState(
            state = state,
            position = 0L,
            generatorProducedMs = 0L,
            playbackSpeed = 1.0f,
            skipForwardAllowed = false,
            errorMessage = null,
        ),
    )

    var playCalls: Int = 0
    var pauseCalls: Int = 0
    var stopCalls: Int = 0
    var skipForwardCalls: Int = 0
    var skipBackCalls: Int = 0
    val seekCalls: MutableList<Long> = mutableListOf()
    val setVolumeCalls: MutableList<Double> = mutableListOf()
    val setShuffledCalls: MutableList<Boolean> = mutableListOf()
    val duckCalls: MutableList<Unit> = mutableListOf()

    override fun metadataState(): SharedFlow<Track?> = metadataSink.asSharedFlow()
    override fun playbackState(): SharedFlow<ChipboxPlaybackState> = playbackSink.asSharedFlow()
    override fun sessionState(): SharedFlow<Session?> = sessionSink.asSharedFlow()
    override fun errorEvents(): SharedFlow<PlayerErrorEvent> = errorSink.asSharedFlow()

    override fun start(session: Session) = Unit
    override fun start(setlist: List<Long>, startingPosition: Int, sourceName: String?, shuffled: Boolean) = Unit
    override fun play() { playCalls++ }
    override fun pause() { pauseCalls++ }
    override fun stop() { stopCalls++ }
    override fun seek(positionMs: Long) { seekCalls += positionMs }
    override fun skipForward() { skipForwardCalls++ }
    override fun skipBack() { skipBackCalls++ }
    override fun setShuffled(shuffled: Boolean) { setShuffledCalls += shuffled }
    override fun pauseTemporarily() = Unit
    override fun duck() { duckCalls += Unit }
    override fun resumeFocus() = Unit
    override fun setVolume(scale: Double) { setVolumeCalls += scale }
}
