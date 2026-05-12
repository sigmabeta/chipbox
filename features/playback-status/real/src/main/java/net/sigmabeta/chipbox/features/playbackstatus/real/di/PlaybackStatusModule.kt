package net.sigmabeta.chipbox.features.playbackstatus.real.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatusEntryPoint
import net.sigmabeta.chipbox.features.playbackstatus.real.RealPlaybackStatusEntryPoint

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackStatusModule {
    @Binds
    @Singleton
    abstract fun bindEntryPoint(impl: RealPlaybackStatusEntryPoint): PlaybackStatusEntryPoint
}
