package net.sigmabeta.chipbox.player.director.fake

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerErrorEvent
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.player.director.SessionRequest

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
open class FakeDirector : Director {

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
    private val setlistSink = MutableSharedFlow<List<Long>>(
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
        setlistSink.tryEmit(emptyList())
    }

    suspend fun emitMetadata(track: Track?) = metadataSink.emit(track)
    suspend fun emitPlayback(state: ChipboxPlaybackState) = playbackSink.emit(state)
    suspend fun emitSession(session: Session?) = sessionSink.emit(session)
    suspend fun emitSetlist(setlist: List<Long>) = setlistSink.emit(setlist)
    suspend fun emitErrorSink(event: PlayerErrorEvent) = errorSink.emit(event)

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

    /** Every [SessionRequest] submitted, in order — the recording assertions inspect. */
    val requests: MutableList<SessionRequest> = mutableListOf()

    override fun metadataState(): SharedFlow<Track?> = metadataSink.asSharedFlow()
    override fun playbackState(): SharedFlow<ChipboxPlaybackState> = playbackSink.asSharedFlow()
    override fun sessionState(): SharedFlow<Session?> = sessionSink.asSharedFlow()
    override fun setlistState(): SharedFlow<List<Long>> = setlistSink.asSharedFlow()
    override fun errorEvents(): SharedFlow<PlayerErrorEvent> = errorSink.asSharedFlow()

    override fun request(request: SessionRequest) {
        requests += request
    }

    // Convenience views over [requests] for assertions — the same shape the per-method counters
    // had before the Director interface was reified onto SessionRequest.
    val playCalls: Int get() = requests.count { it is SessionRequest.Play }
    val pauseCalls: Int get() = requests.count { it is SessionRequest.Pause }
    val stopCalls: Int get() = requests.count { it is SessionRequest.Stop }
    val skipForwardCalls: Int get() = requests.count { it is SessionRequest.SkipForward }
    val skipBackCalls: Int get() = requests.count { it is SessionRequest.SkipBack }
    val playPositionCalls: List<Int> get() = requests.filterIsInstance<SessionRequest.PlayPosition>().map { it.position }
    val reorderCalls: List<Pair<Int, Int>>
        get() = requests.filterIsInstance<SessionRequest.Reorder>().map { it.fromIndex to it.toIndex }
    val removeTrackCalls: List<Int> get() = requests.filterIsInstance<SessionRequest.RemoveTrack>().map { it.index }
    val seekCalls: List<Long> get() = requests.filterIsInstance<SessionRequest.Seek>().map { it.positionMs }
    val setVolumeCalls: List<Double> get() = requests.filterIsInstance<SessionRequest.SetVolume>().map { it.scale }
    val setShuffledCalls: List<Boolean> get() = requests.filterIsInstance<SessionRequest.SetShuffled>().map { it.shuffled }
    val setRepeatModeCalls: List<RepeatMode>
        get() = requests.filterIsInstance<SessionRequest.SetRepeatMode>().map { it.mode }
    val duckCalls: List<Unit> get() = requests.filter { it is SessionRequest.Duck }.map { }
    val restoreCalls: List<Pair<Session, Long>>
        get() = requests.filterIsInstance<SessionRequest.Restore>().map { it.session to it.positionMs }
}
