package net.sigmabeta.chipbox.player.buffer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import net.sigmabeta.chipbox.player.buffer.BufferDebugSource
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.buffer.real.RealBufferManager
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object BufferModule {
    @Provides
    @Singleton
    fun provideRealBufferManager(hatchet: Hatchet): RealBufferManager = RealBufferManager(hatchet)

    @Provides
    @Singleton
    fun provideConsumerBufferManager(bufferManager: RealBufferManager): ConsumerBufferManager = bufferManager

    @Provides
    @Singleton
    fun provideProducerBufferManager(bufferManager: RealBufferManager): ProducerBufferManager = bufferManager

    @Provides
    @Singleton
    fun provideBufferDebugSource(bufferManager: RealBufferManager): BufferDebugSource = bufferManager
}
