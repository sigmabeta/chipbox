package net.sigmabeta.chipbox.player.generator

import kotlinx.coroutines.flow.Flow
import net.sigmabeta.chipbox.player.common.AudioBuffer

interface Generator {
    fun audioStream(): Flow<AudioBuffer>
}