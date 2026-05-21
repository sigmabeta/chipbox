package net.sigmabeta.chipbox.player.emulators.vgm.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.player.emulators.vgm.VgmEmulator
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object VgmEmulatorModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideVgmEmulator(): VgmEmulator = VgmEmulator
}
