package net.sigmabeta.chipbox.player.emulators.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.emulators.EmulatorProvider
import net.sigmabeta.chipbox.player.emulators.fake.FakeEmulator
import net.sigmabeta.chipbox.player.emulators.gba.GbaEmulator
import net.sigmabeta.chipbox.player.emulators.gme.GmeEmulator
import net.sigmabeta.chipbox.player.emulators.psf.PsfEmulator
import net.sigmabeta.chipbox.player.emulators.ssf.SsfEmulator
import net.sigmabeta.chipbox.player.emulators.twosf.TwosfEmulator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EmulatorModule {
    @Provides
    @Singleton
    internal fun provideEmulatorProvider() = EmulatorProvider(
        listOf(
            TwosfEmulator,
            GbaEmulator,
            GmeEmulator,
            PsfEmulator,
            SsfEmulator,
            FakeEmulator
        )
    )
}