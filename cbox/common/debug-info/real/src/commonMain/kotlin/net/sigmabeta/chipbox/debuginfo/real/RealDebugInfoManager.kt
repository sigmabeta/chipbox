package net.sigmabeta.chipbox.debuginfo.real

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import net.sigmabeta.chipbox.debuginfo.DebugInfoManager
import net.sigmabeta.chipbox.debuginfo.PlaybackDebugInfo
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.buffer.BufferDebugSource
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker

/**
 * Combines the Director's track / playback / session streams with the per-component
 * diagnostic `StateFlow`s into a single [PlaybackDebugInfo] hot state.
 *
 * The Director's three flows are `SharedFlow`s with no initial value (they only emit once
 * playback starts), so each is seeded with `onStart { emit(null) }` — otherwise the
 * combined flow would produce nothing until a session begins and the screen would render
 * empty even though the component flows already have values.
 */
class RealDebugInfoManager(
    director: Director,
    generator: Generator,
    speaker: Speaker,
    bufferDebugSource: BufferDebugSource,
    scope: CoroutineScope,
) : DebugInfoManager {

    // Flow is covariant, so the non-null Director streams widen to nullable here, letting
    // onStart seed an initial null before any session has begun.
    private val trackFlow: Flow<Track?> = director.metadataState()
    private val playbackFlow: Flow<ChipboxPlaybackState?> = director.playbackState()
    private val sessionFlow: Flow<Session?> = director.sessionState()

    private val directorState = combine(
        trackFlow.onStart { emit(null) },
        playbackFlow.onStart { emit(null) },
        sessionFlow.onStart { emit(null) },
    ) { track, playback, session ->
        Triple(track, playback, session)
    }

    private val combined = combine(
        directorState,
        generator.debugInfo(),
        speaker.debugInfo(),
        bufferDebugSource.debugInfo(),
    ) { (track, playback, session), generatorInfo, speakerInfo, bufferInfo ->
        PlaybackDebugInfo(
            track = track,
            playback = playback,
            session = session,
            generator = generatorInfo,
            speaker = speakerInfo,
            buffer = bufferInfo,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = PlaybackDebugInfo(),
    )

    override fun debugInfo(): StateFlow<PlaybackDebugInfo> = combined

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
