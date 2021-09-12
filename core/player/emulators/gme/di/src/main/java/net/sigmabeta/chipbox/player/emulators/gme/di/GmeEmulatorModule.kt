package net.sigmabeta.chipbox.player.emulators.gme.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.emulators.gme.GmeEmulator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GmeEmulatorModule {
    @Provides
    @Singleton
    internal fun provideGmeEmulator() = GmeEmulator
}