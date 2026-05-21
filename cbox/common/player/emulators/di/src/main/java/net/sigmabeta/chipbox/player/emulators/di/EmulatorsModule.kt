package net.sigmabeta.chipbox.player.emulators.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.player.emulators.fake.FakeEmulator
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object EmulatorsModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideFakeEmulator(): FakeEmulator = FakeEmulator
}
