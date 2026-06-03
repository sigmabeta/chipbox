package net.sigmabeta.chipbox.jvm.mediasession

import java.io.File
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.PlayerState
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.types.Variant

// MPRIS identifiers shared between the pure state model and the D-Bus transport
// ([MprisMediaControls]). Kept at file scope so neither class owns them.
internal const val OBJECT_PATH = "/org/mpris/MediaPlayer2"
internal const val BUS_NAME = "org.mpris.MediaPlayer2.chipbox"
internal const val ROOT_IFACE = "org.mpris.MediaPlayer2"
internal const val PLAYER_IFACE = "org.mpris.MediaPlayer2.Player"
internal const val MICROS_PER_MILLI = 1_000L

private const val IDENTITY = "Chipbox"
private const val DESKTOP_ENTRY = "chipbox"
private const val TRACK_PATH_PREFIX = "/net/sigmabeta/chipbox/track/"

internal const val STATUS_PLAYING = "Playing"
private const val STATUS_PAUSED = "Paused"
internal const val STATUS_STOPPED = "Stopped"
private const val LOOP_NONE = "None"
private const val LOOP_TRACK = "Track"
private const val LOOP_PLAYLIST = "Playlist"

private const val META_TRACK_ID = "mpris:trackid"
private const val META_LENGTH = "mpris:length"
private const val META_ART_URL = "mpris:artUrl"
private const val META_TITLE = "xesam:title"
private const val META_ARTIST = "xesam:artist"
private const val META_ALBUM = "xesam:album"
private const val META_TRACK_NUMBER = "xesam:trackNumber"

private const val NORMAL_RATE = 1.0
private const val SEEK_DISCONTINUITY_MS = 2_000L

/** Map an MPRIS `LoopStatus` string back to a [RepeatMode] (used when the OS sets the property). */
internal fun loopStatusToRepeatMode(value: String): RepeatMode = when (value) {
    LOOP_TRACK -> RepeatMode.ONE
    LOOP_PLAYLIST -> RepeatMode.ALL
    else -> RepeatMode.OFF
}

/**
 * Pure MPRIS property model — the source of truth for everything published on the bus, with no
 * D-Bus connection of its own. [MprisMediaControls] owns the transport (export + signal
 * emission) and delegates all state here, so the interesting logic (metadata shaping, status
 * mapping, change detection, seek-discontinuity detection) stays unit-testable headlessly.
 *
 * Each `apply*` method folds in a Director event, mutates the held state, and returns the
 * property change-set the transport should emit via `PropertiesChanged` (empty = emit nothing).
 */
@Suppress("TooManyFunctions")
internal class MprisState {

    var playbackStatus: String = STATUS_STOPPED
        private set
    var positionUs: Long = 0L
        private set

    private var loopStatus = LOOP_NONE
    private var shuffle = false
    private var volume = NORMAL_RATE
    private var canGoNext = false
    private var canGoPrevious = false
    private val metadata = linkedMapOf<String, Variant<*>>()
    private var lastReportedPositionMs = 0L

    /** Rebuild metadata for [track] (null clears it); returns the `Metadata` change-set. */
    fun applyTrack(track: Track?): Map<String, Variant<*>> {
        metadata.clear()
        if (track != null) {
            metadata[META_TRACK_ID] = Variant(DBusPath("$TRACK_PATH_PREFIX${track.id}"))
            if (track.trackLengthMs > 0) {
                metadata[META_LENGTH] = Variant(track.trackLengthMs * MICROS_PER_MILLI)
            }
            metadata[META_TITLE] = Variant(track.title)
            track.artists?.map { it.name }?.takeIf { it.isNotEmpty() }?.let { names ->
                metadata[META_ARTIST] = Variant(names, "as")
            }
            track.game?.title?.let { metadata[META_ALBUM] = Variant(it) }
            if (track.trackNumber > 0) metadata[META_TRACK_NUMBER] = Variant(track.trackNumber)
            artUri(track)?.let { metadata[META_ART_URL] = Variant(it) }
        }
        return mapOf("Metadata" to metadataVariant())
    }

    /** Folded result of a playback state: changed properties + a Seeked position (µs) if any. */
    data class PlaybackChange(val changed: Map<String, Variant<*>>, val seekedUs: Long?)

    fun applyPlayback(state: ChipboxPlaybackState): PlaybackChange {
        val changed = linkedMapOf<String, Variant<*>>()

        val newStatus = state.state.toPlaybackStatus()
        if (newStatus != playbackStatus) {
            playbackStatus = newStatus
            changed["PlaybackStatus"] = Variant(playbackStatus)
        }
        if (state.skipForwardAllowed != canGoNext) {
            canGoNext = state.skipForwardAllowed
            changed["CanGoNext"] = Variant(canGoNext)
        }
        val hasSession = state.state != PlayerState.IDLE && state.state != PlayerState.STOPPED
        if (hasSession != canGoPrevious) {
            canGoPrevious = hasSession
            changed["CanGoPrevious"] = Variant(canGoPrevious)
        }

        positionUs = state.position * MICROS_PER_MILLI
        // Emit Seeked only on a discontinuity (track change resets to ~0; a user seek jumps).
        // Ordinary playback advance is left implicit so we don't signal every audio callback.
        val delta = state.position - lastReportedPositionMs
        val seekedUs = if (delta < 0 || delta > SEEK_DISCONTINUITY_MS) positionUs else null
        lastReportedPositionMs = state.position

        return PlaybackChange(changed, seekedUs)
    }

    fun applySession(session: Session?): Map<String, Variant<*>> {
        val changed = linkedMapOf<String, Variant<*>>()
        val newLoop = session?.repeatMode.toLoopStatus()
        if (newLoop != loopStatus) {
            loopStatus = newLoop
            changed["LoopStatus"] = Variant(loopStatus)
        }
        val newShuffle = session?.shuffled ?: false
        if (newShuffle != shuffle) {
            shuffle = newShuffle
            changed["Shuffle"] = Variant(shuffle)
        }
        return changed
    }

    /** Record a user/OS volume change; returns the `Volume` change-set to signal. */
    fun applyVolume(value: Double): Map<String, Variant<*>> {
        volume = value.coerceAtLeast(0.0)
        return mapOf("Volume" to Variant(volume))
    }

    fun property(interfaceName: String, propertyName: String): Variant<*>? = properties(interfaceName)[propertyName]

    fun properties(interfaceName: String): Map<String, Variant<*>> = when (interfaceName) {
        ROOT_IFACE -> rootProperties()
        PLAYER_IFACE -> playerProperties()
        else -> emptyMap()
    }

    private fun rootProperties(): Map<String, Variant<*>> = linkedMapOf(
        "Identity" to Variant(IDENTITY),
        "DesktopEntry" to Variant(DESKTOP_ENTRY),
        "SupportedUriSchemes" to Variant(emptyList<String>(), "as"),
        "SupportedMimeTypes" to Variant(emptyList<String>(), "as"),
        "HasTrackList" to Variant(false),
        "CanQuit" to Variant(false),
        "CanRaise" to Variant(false),
    )

    private fun playerProperties(): Map<String, Variant<*>> = linkedMapOf(
        "PlaybackStatus" to Variant(playbackStatus),
        "LoopStatus" to Variant(loopStatus),
        "Rate" to Variant(NORMAL_RATE),
        "Shuffle" to Variant(shuffle),
        "Metadata" to metadataVariant(),
        "Volume" to Variant(volume),
        "Position" to Variant(positionUs),
        "MinimumRate" to Variant(NORMAL_RATE),
        "MaximumRate" to Variant(NORMAL_RATE),
        "CanGoNext" to Variant(canGoNext),
        "CanGoPrevious" to Variant(canGoPrevious),
        "CanPlay" to Variant(true),
        "CanPause" to Variant(true),
        "CanSeek" to Variant(true),
        "CanControl" to Variant(true),
    )

    private fun metadataVariant(): Variant<*> = Variant(LinkedHashMap(metadata), "a{sv}")

    /** Resolve a track's cover into a `file:`/`http(s):` URI the OS can fetch, or null. */
    private fun artUri(track: Track): String? {
        val url = track.game?.photoUrl?.takeIf { it.isNotBlank() } ?: return null
        return when {
            url.startsWith("file:") || url.startsWith("http:") || url.startsWith("https:") -> url
            else -> File(url).takeIf { it.exists() }?.toURI()?.toString()
        }
    }

    private fun PlayerState.toPlaybackStatus(): String = when (this) {
        PlayerState.PLAYING, PlayerState.BUFFERING, PlayerState.ENDING -> STATUS_PLAYING
        PlayerState.PAUSED -> STATUS_PAUSED
        PlayerState.IDLE, PlayerState.STOPPED, PlayerState.ERROR -> STATUS_STOPPED
    }

    private fun RepeatMode?.toLoopStatus(): String = when (this) {
        RepeatMode.ONE -> LOOP_TRACK
        RepeatMode.ALL -> LOOP_PLAYLIST
        RepeatMode.OFF, null -> LOOP_NONE
    }
}
