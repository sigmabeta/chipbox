package net.sigmabeta.chipbox.player.emulators.psf.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.player.emulators.psf.PsfEmulator
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object PsfEmulatorModule {
    @Provides
    @SingleIn(AppScope::class)
    fun providePsfEmulator(): PsfEmulator = PsfEmulator
}
