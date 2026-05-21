package net.sigmabeta.chipbox.player.emulators.ssf.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.player.emulators.ssf.SsfEmulator
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object SsfEmulatorModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideSsfEmulator(): SsfEmulator = SsfEmulator
}
