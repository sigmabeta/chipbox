package net.sigmabeta.chipbox.player.status.real.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.status.real.RealStatusProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealStatusModule {
    @Provides
    @Singleton
    internal fun provideRealControls() = RealStatusProvider()

}