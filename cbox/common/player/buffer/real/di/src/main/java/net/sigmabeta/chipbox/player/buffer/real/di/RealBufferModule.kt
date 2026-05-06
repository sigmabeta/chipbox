package net.sigmabeta.chipbox.player.buffer.real.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.buffer.real.RealBufferManager
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealBufferModule {
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