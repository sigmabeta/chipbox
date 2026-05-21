package net.sigmabeta.chipbox.player.emulators.twosf.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.player.emulators.twosf.TwosfEmulator
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object TwosfEmulatorModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideTwosfEmulator(): TwosfEmulator = TwosfEmulator
}
