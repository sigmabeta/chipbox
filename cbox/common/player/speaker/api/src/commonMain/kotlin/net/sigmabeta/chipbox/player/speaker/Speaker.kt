package net.sigmabeta.chipbox.player.speaker

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Consumer side of the playback pipeline. Pulls audio buffers from the upstream producer
 * (Generator → buffer manager → Speaker) and writes them to a sink.
 *
 * Production wiring (Android + JVM apps) goes through [BaseSpeaker], whose subclasses pick the
 * sink: [net.sigmabeta.chipbox.player.speaker.real.RealSpeaker] writes to Android's
 * `AudioTrack`, the JVM counterpart writes to `javax.sound.sampled.SourceDataLine`,
 * [net.sigmabeta.chipbox.player.speaker.file.FileSpeaker] writes to a WAV file, and
 * [net.sigmabeta.chipbox.player.speaker.text.TextSpeaker] writes a level diagram to stdout.
 * Tests can implement this interface directly to drive the
 * [net.sigmabeta.chipbox.player.director.Director] without dragging the rest of the pipeline in.
 */
interface Speaker {

    /** Hot event stream of [SpeakerEvent]s — buffering, playing, track-change, errors.
     *  Consumed by [net.sigmabeta.chipbox.player.director.Director]. */
    fun events(): SharedFlow<SpeakerEvent>

    /** Observational diagnostics for the debug PlaybackStatus screen. */
    fun debugInfo(): StateFlow<SpeakerDebugInfo>

    /**
     * Milliseconds played within the currently-loaded track, derived from the sink's play head.
     * Sinks that don't track a play head (test/file sinks) return 0.
     */
    fun currentPositionMs(): Long

    /** Cancel the speaker's owning coroutine scope. After [release] the speaker is unusable —
     *  call only at app shutdown. */
    fun release()

    /** Start the consume loop if it isn't already running. */
    fun play()

    /** Cancel the consume loop without releasing the sink — the speaker can be resumed with
     *  [play]. */
    suspend fun pause()

    /** Cancel the consume loop, reset speaker state, and tear down the sink. */
    suspend fun stop()

    /** Cancel the consume loop, drain pending buffers, flush the sink, and restart consumption.
     *  Used by the director during an in-track seek so post-seek audio isn't preceded by stale
     *  frames already in flight. */
    suspend fun seek()

    /** Like [seek], but for a forced *track* change: discards every buffer that isn't from
     *  [trackId] until the new track's first buffer arrives, then announces the change. */
    suspend fun switchTo(trackId: Long)

    /** Duck output to 50% while [ducked] (transient OS audio-focus loss), restoring full
     *  volume when not. */
    fun setDucked(ducked: Boolean)

    /** Set an arbitrary master output volume; `1.0` leaves audio unchanged, `0.0` silences it,
     *  `1.5` boosts it by 50%. Independent of the fade-out and of ducking. */
    fun setVolume(scale: Double)

    /** Register an arbitrary, independently-keyed volume modification. */
    fun setVolumeModification(key: String, scale: Double)

    /** Remove a previously registered [setVolumeModification]. */
    fun clearVolumeModification(key: String)
}
