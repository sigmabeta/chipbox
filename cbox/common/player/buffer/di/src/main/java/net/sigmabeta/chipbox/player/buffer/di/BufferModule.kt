package net.sigmabeta.chipbox.player.buffer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.buffer.real.RealBufferManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BufferModule {
    @Provides
    @Singleton
    fun provideRealBufferManager() = RealBufferManager()

    @Provides
    @Singleton
    fun provideConsumerBufferManager(bufferManager: RealBufferManager): ConsumerBufferManager = bufferManager

    @Provides
    @Singleton
    fun provideProducerBufferManager(bufferManager: RealBufferManager): ProducerBufferManager = bufferManager
}
