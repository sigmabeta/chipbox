package net.sigmabeta.chipbox.player.controls.real

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.player.controls.Controls
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker

class RealControls(
        private val generator: Generator,
        private val speaker: Speaker,
        private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : Controls {
    private val controlScope = CoroutineScope(dispatcher)

    override fun play(trackId: Long) {
        controlScope.launch {
            generator.startTrack(trackId)
            speaker.play()
        }
    }
}