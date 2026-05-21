package net.sigmabeta.chipbox.player.emulators.gme.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.player.emulators.gme.GmeEmulator
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object GmeEmulatorModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideGmeEmulator(): GmeEmulator = GmeEmulator
}
