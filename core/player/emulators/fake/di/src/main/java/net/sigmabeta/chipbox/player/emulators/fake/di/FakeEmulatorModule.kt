package net.sigmabeta.chipbox.player.emulators.fake.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.emulators.fake.FakeEmulator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FakeEmulatorModule {
    @Provides
    @Singleton
    internal fun provideFakeEmulator() = FakeEmulator
}