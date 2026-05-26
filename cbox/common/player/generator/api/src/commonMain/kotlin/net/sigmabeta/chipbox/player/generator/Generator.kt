package net.sigmabeta.chipbox.player.generator

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Producer side of the playback pipeline. Resolves a track id to bytes via the configured
 * repository + content-source registry, decodes it to PCM, and pushes the result into a
 * downstream buffer manager for a [net.sigmabeta.chipbox.player.speaker.Speaker] to consume.
 *
 * Production wiring (Android + JVM apps) goes through [BaseGenerator], whose two subclasses
 * pick the decoding path: [net.sigmabeta.chipbox.player.generator.real.RealGenerator] dispatches
 * across the available native emulators with a render-ahead cache;
 * [net.sigmabeta.chipbox.player.generator.fake.SynthGenerator] uses an in-process synth. Tests
 * can implement this interface directly to drive the
 * [net.sigmabeta.chipbox.player.director.Director] without dragging the rest of the pipeline in.
 *
 * ### Threading
 * The generation loop runs on a single coroutine ([BaseGenerator] uses its constructor's
 * `dispatcher`). [play], [pause], [stop], and [seek] manipulate that job; calls from any
 * thread are safe but non-atomic with respect to each other.
 *
 * ### Track transitions
 * [startTrack] queues the next track id and starts the loop if it isn't running. When a track
 * ends naturally, the loop emits [GeneratorEvent.TrackChange] and waits for the next id from
 * the director.
 */
interface Generator {

    /** Hot event stream of [GeneratorEvent]s — loading, emitting buffers, errors, track-change
     *  requests. Consumed by [net.sigmabeta.chipbox.player.director.Director]. */
    fun events(): SharedFlow<GeneratorEvent>

    /** Observational diagnostics for the debug PlaybackStatus screen. */
    fun debugInfo(): StateFlow<GeneratorDebugInfo>

    /** Cancel the generator's owning coroutine scope. After [release] the generator is unusable
     *  — call only at app shutdown. */
    fun release()

    /** Queue [trackId] as the next track. If the loop isn't running, start it; the queued id
     *  is consumed at the next buffer boundary. Suspends only until the channel send succeeds. */
    suspend fun startTrack(trackId: Long)

    /** Start the generation loop if it isn't already running. Safe to call repeatedly. */
    fun play()

    /** Cancel the generation loop without tearing down the current track state. The loop can
     *  be resumed with [play] or [startTrack]. */
    fun pause()

    /** Cancel the generation loop and tear down the current track. */
    suspend fun stop()

    /** Reposition playback within the current track to [positionMs]. Takes effect at the next
     *  buffer boundary; the caller drains and flushes the speaker so the seek isn't preceded
     *  by stale frames. */
    suspend fun seek(positionMs: Long)
}
