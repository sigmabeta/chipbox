package net.sigmabeta.chipbox.player.generator.real

import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.player.common.AudioBuffer
import net.sigmabeta.chipbox.player.generator.Generator

class RealGenerator() : Generator {
    override fun audioStream(): Flow<AudioBuffer> {
        TODO("Not yet implemented")
    }
}