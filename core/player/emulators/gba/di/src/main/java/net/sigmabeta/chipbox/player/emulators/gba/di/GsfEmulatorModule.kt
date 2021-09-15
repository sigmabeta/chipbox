package net.sigmabeta.chipbox.player.emulators.gba.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.emulators.gba.GbaEmulator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GbaEmulatorModule {
    @Provides
    @Singleton
    internal fun provideGbaEmulator() = GbaEmulator
}