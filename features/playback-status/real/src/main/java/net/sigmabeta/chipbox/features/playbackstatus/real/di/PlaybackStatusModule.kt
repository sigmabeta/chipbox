package net.sigmabeta.chipbox.features.playbackstatus.real.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import javax.inject.Singleton
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatusEntryPoint
import net.sigmabeta.chipbox.features.playbackstatus.real.RealPlaybackStatusEntryPoint
import net.sigmabeta.sage.di.AppScope

// Was Dagger-style abstract @Binds; Metro's Dagger interop doesn't pick up @Binds in
// abstract classes the way @Provides comes through, so rewriting as @Provides on an object
// keeps both DI systems wiring the binding from the same source. Equivalent at runtime —
// Dagger compiles to a Provider that delegates to the @Inject-constructed impl.
@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object PlaybackStatusModule {
    @Provides
    @Singleton
    fun provideEntryPoint(impl: RealPlaybackStatusEntryPoint): PlaybackStatusEntryPoint = impl
}
