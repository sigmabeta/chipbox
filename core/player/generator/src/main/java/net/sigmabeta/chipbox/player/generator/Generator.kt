package net.sigmabeta.chipbox.player.generator

import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.player.common.GeneratorEvent

interface Generator {
    fun audioStream(trackId: Long): Flow<GeneratorEvent>
}