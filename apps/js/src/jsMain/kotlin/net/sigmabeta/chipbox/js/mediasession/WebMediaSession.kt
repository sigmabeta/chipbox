@file:Suppress("FunctionName", "VariableNaming")

package net.sigmabeta.chipbox.js.mediasession

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.PlayerState

/**
 * Bridges the chipbox [Director] to the browser's `navigator.mediaSession` API so OS-level
 * media controls (lock-screen play/pause, media keys, Bluetooth headset buttons,
 * picture-in-picture mini-player) drive the same actions the in-app transport does.
 *
 * Three sides:
 *  1. **Action handlers** — installed once at [install], translate browser-fired media events
 *     into [Director] calls.
 *  2. **Metadata** — observe [Director.metadataState] and push title / artist / album / artwork
 *     into a fresh `MediaMetadata`. Game title goes into `album`, track title into `title`,
 *     artists collapse into `artist`. Artwork is the `Game.photoUrl` already rewritten by the
 *     server into an opaque `/api/games/{id}/cover` endpoint — prepend baseUrl so the OS can
 *     fetch it directly.
 *  3. **Playback state + position** — observe [Director.playbackState] and update both
 *     `playbackState` (drives the play/pause icon on the OS surface) and `setPositionState`
 *     (drives the scrubber + remaining-time readout).
 *
 * **Known limitation (Chromium on Linux):** the play/pause icon on the global media surface
 * doesn't always flip on pause even though we write `playbackState='paused'` and Chrome
 * acknowledges the write. Action handlers still fire correctly (pause / play / skip from
 * external controls all work), and metadata + artwork update on track changes. Other browsers
 * + Chromium on other OSes may behave differently; this code is correct per spec.
 */
class WebMediaSession(
    private val director: Director,
    private val baseUrl: String,
) {

    private val session: MediaSession? = window.navigator.asDynamic().mediaSession?.unsafeCast<MediaSession>()
    private var latestTrack: Track? = null

    fun install(scope: CoroutineScope) {
        val session = session ?: return

        // Action handlers — fire-and-forget; the director is responsible for ignoring no-op
        // requests (e.g. play() while already playing). null clears a handler, which removes
        // its affordance from the OS surface entirely.
        session.setActionHandler("play") { director.play() }
        session.setActionHandler("pause") { director.pause() }
        session.setActionHandler("stop") { director.stop() }
        session.setActionHandler("nexttrack") { director.skipForward() }
        session.setActionHandler("previoustrack") { director.skipBack() }
        session.setActionHandler("seekto") { details ->
            val seconds = details?.seekTime
            if (seconds != null) director.seek((seconds * MILLIS_PER_SECOND).toLong())
        }
        // `seekbackward` / `seekforward` (the +/- N seconds buttons) — bracket the requested
        // offset around the current position. Browser passes `details.seekOffset` in seconds,
        // defaulting to ~10s when absent.
        session.setActionHandler("seekbackward") { details ->
            val offsetSec = details?.seekOffset ?: DEFAULT_SEEK_STEP_SECONDS
            director.seek((currentPositionMs() - (offsetSec * MILLIS_PER_SECOND).toLong()).coerceAtLeast(0L))
        }
        session.setActionHandler("seekforward") { details ->
            val offsetSec = details?.seekOffset ?: DEFAULT_SEEK_STEP_SECONDS
            director.seek(currentPositionMs() + (offsetSec * MILLIS_PER_SECOND).toLong())
        }

        // Independent subscriptions. metadataState wakes the metadata update only on track id
        // changes; playbackState drives both the play/pause icon and the position scrubber
        // separately.
        director.metadataState()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { track ->
                latestTrack = track
                updateMetadata(session, track)
            }
            .launchIn(scope)

        director.playbackState()
            .onEach { playback -> updatePlayback(session, playback, latestTrack) }
            .launchIn(scope)
    }

    private var lastPlaybackState: ChipboxPlaybackState? = null

    private fun currentPositionMs(): Long = lastPlaybackState?.position ?: 0L

    private fun updateMetadata(session: MediaSession, track: Track?) {
        if (track == null) {
            session.metadata = null
            return
        }
        val artwork = track.game?.photoUrl?.let { url ->
            val full = if (url.startsWith("http://") || url.startsWith("https://")) url else "$baseUrl$url"
            val art = js("{}")
            art.src = full
            // "any" tells the OS to use whatever size the server returns rather than filtering
            // against a hardcoded WxH that wouldn't match the actual cover (covers in the wild
            // are arbitrary). MIME type is intentionally omitted — server sets Content-Type
            // accurately based on the file extension, so a hint here would just disagree.
            art.sizes = "any"
            arrayOf(art)
        }
        val init = js("{}")
        init.title = track.title
        init.album = track.game?.title.orEmpty()
        init.artist = track.artists?.joinToString(", ") { it.name }.orEmpty()
        if (artwork != null) init.artwork = artwork
        session.metadata = MediaMetadata(init)
    }

    private fun updatePlayback(session: MediaSession, playback: ChipboxPlaybackState, track: Track?) {
        lastPlaybackState = playback
        val isPaused = playback.state == PlayerState.PAUSED
        session.playbackState = when (playback.state) {
            PlayerState.PLAYING, PlayerState.BUFFERING, PlayerState.ENDING -> "playing"
            PlayerState.PAUSED -> "paused"
            PlayerState.IDLE, PlayerState.STOPPED, PlayerState.ERROR -> "none"
        }

        val durationMs = track?.trackLengthMs ?: 0L
        // Skip setPositionState when paused — spec only allows positive playbackRate, so we
        // can't signal pause through it. The OS holds the last-reported position across a pause.
        if (!isPaused && durationMs > 0 && playback.position >= 0) {
            val positionState = js("{}")
            positionState.duration = durationMs / MILLIS_PER_SECOND.toDouble()
            positionState.position = playback.position / MILLIS_PER_SECOND.toDouble()
            positionState.playbackRate = playback.playbackSpeed.toDouble().coerceAtLeast(0.01)
            // setPositionState throws if position > duration. Coerce here to avoid noisy
            // exceptions during the brief window right after a track change where Director's
            // playback flow can lag the metadata flow by a frame.
            if (positionState.position.unsafeCast<Double>() > positionState.duration.unsafeCast<Double>()) {
                positionState.position = positionState.duration
            }
            session.setPositionState(positionState)
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val DEFAULT_SEEK_STEP_SECONDS = 10.0
    }
}

// Minimum-surface Web API bindings — kotlin/js stdlib doesn't ship MediaSession declarations.

private external interface MediaSession {
    var metadata: MediaMetadata?
    var playbackState: String // "none" | "paused" | "playing"
    fun setActionHandler(action: String, handler: ((MediaSessionActionDetails?) -> Unit)?)
    fun setPositionState(state: dynamic)
}

private external interface MediaSessionActionDetails {
    val action: String
    val seekOffset: Double?
    val seekTime: Double?
    val fastSeek: Boolean?
}

@JsName("MediaMetadata")
private external class MediaMetadata(init: dynamic)
