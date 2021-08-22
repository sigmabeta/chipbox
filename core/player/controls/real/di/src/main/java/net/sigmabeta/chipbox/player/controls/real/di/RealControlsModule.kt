package net.sigmabeta.chipbox.player.controls.real.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.controls.real.RealControls
import net.sigmabeta.chipbox.player.generator.Generator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RealControlsModule {
    @Provides
    @Singleton
    internal fun provideRealControls(generator: Generator) = RealControls(generator)

}