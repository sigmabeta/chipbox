package net.sigmabeta.chipbox.jvm.mediasession

import kotlinx.coroutines.CoroutineScope
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.sage.logging.Hatchet

/**
 * Platform-agnostic seam for integrating the desktop app with the OS's media controls (the
 * Linux/GNOME media widget, Windows SMTC, macOS Now Playing). [install] hooks the [Director] up
 * to the OS surface for the lifetime of the app; [close] tears it down.
 *
 * Today only Linux (MPRIS over D-Bus — [MprisMediaControls]) has a backend; other platforms get
 * [NoOpMediaControls]. Adding Windows/macOS is a matter of writing another implementation and
 * dispatching to it in [createDesktopMediaControls].
 */
interface DesktopMediaControls : AutoCloseable {
    fun install(scope: CoroutineScope)

    override fun close()
}

/** Fallback used on platforms without a media-control backend yet. */
internal object NoOpMediaControls : DesktopMediaControls {
    override fun install(scope: CoroutineScope) = Unit

    override fun close() = Unit
}

/**
 * Select the media-control backend for the current OS. Never throws — an unsupported OS (or a
 * later failure opening the bus inside [DesktopMediaControls.install]) degrades to a no-op so the
 * app runs unchanged.
 */
fun createDesktopMediaControls(director: Director, hatchet: Hatchet): DesktopMediaControls {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        listOf("nux", "nix", "aix").any { os.contains(it) } -> MprisMediaControls(director, hatchet)

        else -> {
            hatchet.i("No OS media-control backend for '$os'; media controls disabled.")
            NoOpMediaControls
        }
    }
}
