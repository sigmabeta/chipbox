package net.sigmabeta.chipbox.player.emulators.usf.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.player.emulators.usf.UsfEmulator
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object UsfEmulatorModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideUsfEmulator(): UsfEmulator = UsfEmulator
}
