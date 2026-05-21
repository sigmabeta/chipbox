package net.sigmabeta.chipbox.player.buffer.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.player.buffer.BufferDebugSource
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.buffer.real.RealBufferManager
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet

@BindingContainer
@ContributesTo(AppScope::class)
object BufferModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideRealBufferManager(hatchet: Hatchet): RealBufferManager = RealBufferManager(hatchet)

    @Provides
    @SingleIn(AppScope::class)
    fun provideConsumerBufferManager(bufferManager: RealBufferManager): ConsumerBufferManager = bufferManager

    @Provides
    @SingleIn(AppScope::class)
    fun provideProducerBufferManager(bufferManager: RealBufferManager): ProducerBufferManager = bufferManager

    @Provides
    @SingleIn(AppScope::class)
    fun provideBufferDebugSource(bufferManager: RealBufferManager): BufferDebugSource = bufferManager
}
