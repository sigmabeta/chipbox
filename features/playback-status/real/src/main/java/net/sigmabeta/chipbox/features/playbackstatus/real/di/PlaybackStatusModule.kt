package net.sigmabeta.chipbox.features.playbackstatus.real.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import javax.inject.Singleton
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatusEntryPoint
import net.sigmabeta.chipbox.features.playbackstatus.real.RealPlaybackStatusEntryPoint
import net.sigmabeta.sage.di.AppScope

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
abstract class PlaybackStatusModule {
    @Binds
    abstract fun bindEntryPoint(impl: RealPlaybackStatusEntryPoint): PlaybackStatusEntryPoint
}
