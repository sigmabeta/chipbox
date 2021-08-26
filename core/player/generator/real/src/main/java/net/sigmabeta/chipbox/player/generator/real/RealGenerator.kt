package net.sigmabeta.chipbox.player.generator.real

import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.player.common.GeneratorEvent
import net.sigmabeta.chipbox.player.generator.Generator

class RealGenerator() : Generator {
    override fun audioStream(trackId: Long): Flow<GeneratorEvent> {
        TODO("Not yet implemented")
    }
}