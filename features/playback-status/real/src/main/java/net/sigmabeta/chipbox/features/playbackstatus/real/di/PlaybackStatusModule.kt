package net.sigmabeta.chipbox.features.playbackstatus.real.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.features.playbackstatus.PlaybackStatusEntryPoint
import net.sigmabeta.chipbox.features.playbackstatus.real.RealPlaybackStatusEntryPoint
import net.sigmabeta.sage.di.AppScope

// Was Dagger-style abstract @Binds; Metro's Dagger interop doesn't pick up @Binds in
// abstract classes the way @Provides comes through, so rewriting as @Provides on an object
// keeps both DI systems wiring the binding from the same source. Equivalent at runtime —
// Dagger compiles to a Provider that delegates to the @Inject-constructed impl.
@BindingContainer
@ContributesTo(AppScope::class)
object PlaybackStatusModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideEntryPoint(impl: RealPlaybackStatusEntryPoint): PlaybackStatusEntryPoint = impl
}
