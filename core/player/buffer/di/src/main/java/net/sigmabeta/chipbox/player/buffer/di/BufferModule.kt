package net.sigmabeta.chipbox.player.buffer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.buffer.real.RealBufferManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BufferModule {
    /**
     * No-op... for now.
     */
}