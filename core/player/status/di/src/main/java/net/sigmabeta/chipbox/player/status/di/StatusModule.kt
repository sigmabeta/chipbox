package net.sigmabeta.chipbox.player.status.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.status.StatusProvider
import net.sigmabeta.chipbox.player.status.real.RealStatusProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StatusModule {
    @Provides
    @Singleton
    internal fun provideStatusProvider(realStatusProvider: RealStatusProvider): StatusProvider =
        realStatusProvider

}