package net.sigmabeta.chipbox.features.playbackstatus.real

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.zacsweers.metro.Inject
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatus
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatusEntryPoint

class RealPlaybackStatusEntryPoint @Inject constructor() : PlaybackStatusEntryPoint {
    override val isAvailable: Boolean = true

    override fun register(builder: NavGraphBuilder, onEvent: (ChipboxEvent) -> Unit) {
        builder.composable<PlaybackStatus> { PlaybackStatusRoute(onEvent) }
    }
}
