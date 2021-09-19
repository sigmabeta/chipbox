package net.sigmabeta.chipbox.player.emulators.ssf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.emulators.ssf.SsfEmulator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SsfEmulatorModule {
    @Provides
    @Singleton
    internal fun provideSsfEmulator() = SsfEmulator
}