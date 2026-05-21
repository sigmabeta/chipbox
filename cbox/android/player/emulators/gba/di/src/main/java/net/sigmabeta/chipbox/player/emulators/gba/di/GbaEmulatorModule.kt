package net.sigmabeta.chipbox.player.emulators.gba.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.player.emulators.gba.GbaEmulator
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object GbaEmulatorModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideGbaEmulator(): GbaEmulator = GbaEmulator
}
