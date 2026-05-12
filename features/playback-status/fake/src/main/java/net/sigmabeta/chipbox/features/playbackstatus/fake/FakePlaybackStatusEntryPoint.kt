package net.sigmabeta.chipbox.features.playbackstatus.fake

import androidx.navigation.NavGraphBuilder
import javax.inject.Inject
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatusEntryPoint

class FakePlaybackStatusEntryPoint @Inject constructor() : PlaybackStatusEntryPoint {
    override val isAvailable: Boolean = false
    override fun register(builder: NavGraphBuilder, onEvent: (ChipboxEvent) -> Unit) = Unit
}
