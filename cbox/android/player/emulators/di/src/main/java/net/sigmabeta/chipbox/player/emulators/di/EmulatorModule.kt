package net.sigmabeta.chipbox.player.emulators.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.player.emulators.EmulatorProvider
import net.sigmabeta.chipbox.player.emulators.fake.FakeEmulator
import net.sigmabeta.chipbox.player.emulators.gba.GbaEmulator
import net.sigmabeta.chipbox.player.emulators.gme.GmeEmulator
import net.sigmabeta.chipbox.player.emulators.psf.PsfEmulator
import net.sigmabeta.chipbox.player.emulators.ssf.SsfEmulator
import net.sigmabeta.chipbox.player.emulators.twosf.TwosfEmulator
import net.sigmabeta.chipbox.player.emulators.usf.UsfEmulator
import net.sigmabeta.chipbox.player.emulators.vgm.VgmEmulator
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object EmulatorModule {
    @Provides
    @SingleIn(AppScope::class)
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
