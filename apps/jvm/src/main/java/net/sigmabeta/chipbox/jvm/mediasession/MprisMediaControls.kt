@file:Suppress("FunctionName")

package net.sigmabeta.chipbox.jvm.mediasession

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.sage.logging.Hatchet
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

/**
 * Linux backend for [DesktopMediaControls]: exposes the player on the session bus as an MPRIS
 * media player so GNOME's media widget, KDE, `playerctl`, and Bluetooth headset keys can drive
 * the same [Director] the in-app transport does.
 *
 * One object is exported at `/org/mpris/MediaPlayer2` implementing both MPRIS interfaces plus the
 * standard `org.freedesktop.DBus.Properties`. Two directions of data flow:
 *  - **inbound** — external play/pause/next/seek calls land on the `MediaPlayer2*` methods, which
 *    forward to the [Director]. These run on dbus-java's reader thread; the Director's transport
 *    methods are imperative entry points safe to call from any thread.
 *  - **outbound** — Director metadata / playback / session flows are folded into [MprisState],
 *    whose change-sets we push out via `PropertiesChanged` (and `Seeked` on discontinuities).
 *
 * This class is the thin transport adapter; all property logic lives in the pure [MprisState].
 * Construction is cheap (no I/O); the bus connection is opened in [install] so a missing session
 * bus (headless) degrades to "no media controls" rather than failing app startup.
 */
@Suppress("TooManyFunctions")
class MprisMediaControls(
    private val director: Director,
    private val hatchet: Hatchet,
) : MediaPlayer2,
    MediaPlayer2Player,
    Properties,
    DesktopMediaControls {

    private val state = MprisState()
    private var connection: DBusConnection? = null

    override fun getObjectPath(): String = OBJECT_PATH

    @Suppress("TooGenericExceptionCaught")
    override fun install(scope: CoroutineScope) {
        connection = try {
            DBusConnectionBuilder.forSessionBus().build().apply {
                requestBusName(BUS_NAME)
                exportObject(OBJECT_PATH, this@MprisMediaControls)
            }
        } catch (e: Exception) {
            hatchet.w("MPRIS media controls unavailable: ${e.message}")
            return
        }

        director.metadataState()
            .distinctUntilChanged { a, b -> a?.id == b?.id }
            .onEach { track -> signal(PLAYER_IFACE, state.applyTrack(track)) }
            .launchIn(scope)

        director.playbackState()
            .onEach { playback ->
                val change = state.applyPlayback(playback)
                if (change.changed.isNotEmpty()) signal(PLAYER_IFACE, change.changed)
                change.seekedUs?.let { sendSeeked(it) }
            }
            .launchIn(scope)

        director.sessionState()
            .onEach { session ->
                val changed = state.applySession(session)
                if (changed.isNotEmpty()) signal(PLAYER_IFACE, changed)
            }
            .launchIn(scope)

        hatchet.i("MPRIS media controls registered as $BUS_NAME.")
    }

    @Suppress("TooGenericExceptionCaught")
    override fun close() {
        val conn = connection ?: return
        connection = null
        try {
            conn.unExportObject(OBJECT_PATH)
            conn.releaseBusName(BUS_NAME)
            conn.disconnect()
        } catch (e: Exception) {
            hatchet.w("Error tearing down MPRIS controls: ${e.message}")
        }
    }

    // --- Inbound: bus methods -> Director ------------------------------------------------------

    override fun Play() = director.play()

    override fun Pause() = director.pause()

    override fun Stop() = director.stop()

    override fun Next() = director.skipForward()

    override fun Previous() = director.skipBack()

    override fun PlayPause() {
        if (state.playbackStatus == STATUS_PLAYING) director.pause() else director.play()
    }

    override fun Seek(offsetUs: Long) {
        director.seek(((state.positionUs + offsetUs).coerceAtLeast(0L)) / MICROS_PER_MILLI)
    }

    override fun SetPosition(trackId: DBusPath, positionUs: Long) {
        director.seek(positionUs.coerceAtLeast(0L) / MICROS_PER_MILLI)
    }

    override fun OpenUri(uri: String) = Unit

    override fun Raise() = Unit

    override fun Quit() = Unit

    // --- org.freedesktop.DBus.Properties -------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    override fun <A> Get(interfaceName: String, propertyName: String): A =
        (
            state.property(interfaceName, propertyName)
            ?: throw DBusExecutionException("No such property $interfaceName.$propertyName")
        ) as A

    override fun <A> Set(interfaceName: String, propertyName: String, value: A) {
        when (propertyName) {
            "LoopStatus" -> director.setRepeatMode(loopStatusToRepeatMode(value as String))

            "Shuffle" -> director.setShuffled(value as Boolean)

            "Volume" -> {
                val scale = value as Double
                director.setVolume(scale)
                signal(PLAYER_IFACE, state.applyVolume(scale))
            }

            // Rate (and anything else) isn't user-settable on a chiptune player.
            else -> Unit
        }
    }

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> = state.properties(interfaceName)

    // --- Signaling -----------------------------------------------------------------------------

    private fun signal(interfaceName: String, changed: Map<String, Variant<*>>) {
        val conn = connection ?: return
        try {
            conn.sendMessage(Properties.PropertiesChanged(OBJECT_PATH, interfaceName, changed, emptyList()))
        } catch (e: DBusException) {
            hatchet.w("Failed to emit PropertiesChanged: ${e.message}")
        }
    }

    private fun sendSeeked(positionUs: Long) {
        val conn = connection ?: return
        try {
            conn.sendMessage(MediaPlayer2Player.Seeked(OBJECT_PATH, positionUs))
        } catch (e: DBusException) {
            hatchet.w("Failed to emit Seeked: ${e.message}")
        }
    }
}
