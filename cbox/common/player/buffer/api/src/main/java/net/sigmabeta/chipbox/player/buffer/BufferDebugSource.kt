package net.sigmabeta.chipbox.player.buffer

import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only diagnostic view of the buffer manager. Bound to the same [RealBufferManager]
 * instance as [ProducerBufferManager] / [ConsumerBufferManager] but kept separate so
 * neither pipeline side gains access to debug plumbing.
 */
interface BufferDebugSource {
    fun debugInfo(): StateFlow<BufferDebugInfo>
}
