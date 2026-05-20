package net.sigmabeta.chipbox.player.emulators.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import net.sigmabeta.chipbox.player.emulators.EmulatorProvider
import net.sigmabeta.chipbox.player.emulators.fake.FakeEmulator
import net.sigmabeta.chipbox.player.emulators.gba.GbaEmulator
import net.sigmabeta.chipbox.player.emulators.gme.GmeEmulator
import net.sigmabeta.chipbox.player.emulators.psf.PsfEmulator
import net.sigmabeta.chipbox.player.emulators.ssf.SsfEmulator
import net.sigmabeta.chipbox.player.emulators.twosf.TwosfEmulator
import net.sigmabeta.chipbox.player.emulators.usf.UsfEmulator
import net.sigmabeta.chipbox.player.emulators.vgm.VgmEmulator
import javax.inject.Singleton
import net.sigmabeta.sage.di.AppScope

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object EmulatorModule {
    @Provides
    @Singleton
    fun provideEmulatorProvider(): EmulatorProvider = EmulatorProvider(
        listOf(
            TwosfEmulator,
            GbaEmulator,
            GmeEmulator,
            PsfEmulator,
            SsfEmulator,
            VgmEmulator,
            UsfEmulator,
            FakeEmulator
        )
    )
}
