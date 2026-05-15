package net.sigmabeta.chipbox.player.buffer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.buffer.real.RealBufferManager
import net.sigmabeta.sage.logging.Hatchet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BufferModule {
    @Provides
    @Singleton
    fun provideRealBufferManager(hatchet: Hatchet) = RealBufferManager(hatchet)

    @Provides
    @Singleton
    fun provideConsumerBufferManager(bufferManager: RealBufferManager): ConsumerBufferManager = bufferManager

    @Provides
    @Singleton
    fun provideProducerBufferManager(bufferManager: RealBufferManager): ProducerBufferManager = bufferManager
}
