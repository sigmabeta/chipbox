@file:Suppress("FunctionName")

package net.sigmabeta.chipbox.jvm.mediasession

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusProperty
import org.freedesktop.dbus.annotations.DBusProperty.Access
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal

/**
 * Minimal Kotlin declarations of the two MPRIS D-Bus interfaces a media player exposes so that
 * Linux desktop environments (GNOME's media controls, KDE, `playerctl`, Bluetooth headset keys)
 * can drive playback. We implement them in [MprisMediaControls] and export one object at
 * `/org/mpris/MediaPlayer2`.
 *
 * The `@DBusProperty` annotations only feed dbus-java's introspection XML — the actual property
 * values are served through the [org.freedesktop.dbus.interfaces.Properties] `Get`/`GetAll`
 * implementation and pushed via `PropertiesChanged` signals. Method names are PascalCase because
 * they must match the wire names in the MPRIS spec exactly; hence the file-level `FunctionName`
 * suppression.
 *
 * See the spec: https://specifications.freedesktop.org/mpris-spec/latest/
 */
@DBusInterfaceName("org.mpris.MediaPlayer2")
@DBusProperty(name = "Identity", type = String::class, access = Access.READ)
@DBusProperty(name = "DesktopEntry", type = String::class, access = Access.READ)
@DBusProperty(name = "SupportedMimeTypes", type = Array<String>::class, access = Access.READ)
@DBusProperty(name = "SupportedUriSchemes", type = Array<String>::class, access = Access.READ)
@DBusProperty(name = "HasTrackList", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanQuit", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanRaise", type = Boolean::class, access = Access.READ)
interface MediaPlayer2 : DBusInterface {
    /** Bring the player's window to the foreground. We have no addressable window handle from a
     *  Compose `application {}`, so this is a no-op. */
    fun Raise()

    /** Quit the player. Also a no-op — closing from an external surface isn't wired. */
    fun Quit()
}

/**
 * `org.mpris.MediaPlayer2.Player` — the transport surface: play/pause/stop/next/previous, seek,
 * and the properties that drive the OS readout (status, metadata, position, capabilities).
 */
@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
@DBusProperty(name = "PlaybackStatus", type = String::class, access = Access.READ)
@DBusProperty(name = "LoopStatus", type = String::class)
@DBusProperty(name = "Rate", type = Double::class)
@DBusProperty(name = "Shuffle", type = Boolean::class)
@DBusProperty(name = "Metadata", type = Map::class, access = Access.READ)
@DBusProperty(name = "Volume", type = Double::class)
@DBusProperty(name = "Position", type = Long::class, access = Access.READ)
@DBusProperty(name = "MinimumRate", type = Double::class, access = Access.READ)
@DBusProperty(name = "MaximumRate", type = Double::class, access = Access.READ)
@DBusProperty(name = "CanGoNext", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanGoPrevious", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanPlay", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanPause", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanSeek", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanControl", type = Boolean::class, access = Access.READ)
interface MediaPlayer2Player : DBusInterface {
    fun Next()

    fun Previous()

    fun Pause()

    fun PlayPause()

    fun Stop()

    fun Play()

    /** Seek by [offsetUs] microseconds relative to the current position (may be negative). */
    fun Seek(offsetUs: Long)

    /** Seek to the absolute [positionUs] (microseconds) within the track identified by [trackId]. */
    fun SetPosition(trackId: DBusPath, positionUs: Long)

    /** Open a URI directly. We have no URI scheme registered, so this is a no-op. */
    fun OpenUri(uri: String)

    /**
     * Emitted on a discontinuous position change (a user seek, a track change) so external
     * scrubbers resync. Steady playback advances are inferred by clients from `Rate` instead.
     * [positionUs] is passed to the `DBusSignal` vararg super-constructor so it becomes the
     * signal's `x` (int64) body argument, per the MPRIS spec.
     */
    class Seeked(path: String, positionUs: Long) : DBusSignal(path, positionUs)
}
