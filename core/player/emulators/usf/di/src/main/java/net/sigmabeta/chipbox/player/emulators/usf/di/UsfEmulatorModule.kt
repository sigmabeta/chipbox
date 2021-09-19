package net.sigmabeta.chipbox.player.emulators.usf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.emulators.usf.UsfEmulator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UsfEmulatorModule {
    @Provides
    @Singleton
    internal fun provideUsfEmulator() = UsfEmulator
}