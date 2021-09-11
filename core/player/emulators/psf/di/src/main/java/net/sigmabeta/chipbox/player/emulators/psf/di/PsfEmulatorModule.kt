package net.sigmabeta.chipbox.player.emulators.psf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.emulators.psf.PsfEmulator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PsfEmulatorModule {
    @Provides
    @Singleton
    internal fun providePsfEmulator() = PsfEmulator
}