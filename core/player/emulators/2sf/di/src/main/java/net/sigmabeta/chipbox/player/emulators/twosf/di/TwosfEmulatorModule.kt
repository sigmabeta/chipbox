package net.sigmabeta.chipbox.player.emulators.twosf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.emulators.twosf.TwosfEmulator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TwosfEmulatorModule {
    @Provides
    @Singleton
    internal fun provideTwosfEmulator() = TwosfEmulator
}