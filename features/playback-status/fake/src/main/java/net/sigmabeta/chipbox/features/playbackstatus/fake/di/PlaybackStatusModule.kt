package net.sigmabeta.chipbox.features.playbackstatus.fake.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatusEntryPoint
import net.sigmabeta.chipbox.features.playbackstatus.fake.FakePlaybackStatusEntryPoint

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackStatusModule {
    @Binds
    @Singleton
    abstract fun bindEntryPoint(impl: FakePlaybackStatusEntryPoint): PlaybackStatusEntryPoint
}
