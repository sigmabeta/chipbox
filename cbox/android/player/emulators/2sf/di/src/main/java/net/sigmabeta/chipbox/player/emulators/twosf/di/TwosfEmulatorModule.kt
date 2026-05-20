package net.sigmabeta.chipbox.player.emulators.twosf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import net.sigmabeta.chipbox.player.emulators.twosf.TwosfEmulator
import javax.inject.Singleton
import net.sigmabeta.sage.di.AppScope

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object TwosfEmulatorModule {
    @Provides
    @Singleton
    fun provideTwosfEmulator(): TwosfEmulator = TwosfEmulator
}
