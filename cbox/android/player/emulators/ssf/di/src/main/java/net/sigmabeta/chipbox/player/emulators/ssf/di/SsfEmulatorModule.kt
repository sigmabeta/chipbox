package net.sigmabeta.chipbox.player.emulators.ssf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import net.sigmabeta.chipbox.player.emulators.ssf.SsfEmulator
import javax.inject.Singleton
import net.sigmabeta.sage.di.AppScope

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object SsfEmulatorModule {
    @Provides
    @Singleton
    fun provideSsfEmulator(): SsfEmulator = SsfEmulator
}
