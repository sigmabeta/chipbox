package net.sigmabeta.chipbox.features.playbackstatus

import androidx.navigation.NavGraphBuilder
import net.sigmabeta.chipbox.appcomm.ChipboxEvent

/**
 * Variant-selected hook for the Playback Status debug screen. The release build receives a
 * fake implementation that reports unavailable and registers nothing, so the destination
 * isn't reachable and the entry row in Settings is suppressed; the debug build receives the
 * real implementation that wires up [register] to add `composable<PlaybackStatus>` to the
 * caller's [NavGraphBuilder].
 */
interface PlaybackStatusEntryPoint {
    val isAvailable: Boolean
    fun register(builder: NavGraphBuilder, onEvent: (ChipboxEvent) -> Unit)
}
