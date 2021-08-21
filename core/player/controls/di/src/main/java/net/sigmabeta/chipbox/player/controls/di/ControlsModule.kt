package net.sigmabeta.chipbox.player.controls.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.controls.Controls
import net.sigmabeta.chipbox.player.controls.real.RealControls
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ControlsModule {
    @Provides
    @Singleton
    internal fun provideControls(realControls: RealControls): Controls = realControls

}