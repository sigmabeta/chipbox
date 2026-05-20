package net.sigmabeta.chipbox.player.emulators.psf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import net.sigmabeta.chipbox.player.emulators.psf.PsfEmulator
import javax.inject.Singleton
import net.sigmabeta.sage.di.AppScope

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object PsfEmulatorModule {
    @Provides
    @Singleton
    fun providePsfEmulator(): PsfEmulator = PsfEmulator
}
