package net.sigmabeta.chipbox.player.emulators.gba.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import net.sigmabeta.chipbox.player.emulators.gba.GbaEmulator
import javax.inject.Singleton
import net.sigmabeta.sage.di.AppScope

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object GbaEmulatorModule {
    @Provides
    @Singleton
    fun provideGbaEmulator(): GbaEmulator = GbaEmulator
}
