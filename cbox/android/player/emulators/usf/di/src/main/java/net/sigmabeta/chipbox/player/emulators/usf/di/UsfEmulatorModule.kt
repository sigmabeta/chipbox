package net.sigmabeta.chipbox.player.emulators.usf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import net.sigmabeta.chipbox.player.emulators.usf.UsfEmulator
import javax.inject.Singleton
import net.sigmabeta.sage.di.AppScope

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object UsfEmulatorModule {
    @Provides
    @Singleton
    fun provideUsfEmulator(): UsfEmulator = UsfEmulator
}
