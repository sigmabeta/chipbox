package net.sigmabeta.chipbox.player.emulators.vgm.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.emulators.vgm.VgmEmulator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VgmEmulatorModule {
    @Provides
    @Singleton
    fun provideVgmEmulator() = VgmEmulator
}