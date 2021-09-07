package net.sigmabeta.chipbox.player.generator.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.generator.fake.FakeGenerator
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GeneratorModule {
    @Provides
    @Singleton
    internal fun provideGenerator(
        fakeGenerator: FakeGenerator,
        realGenerator: RealGenerator
    ): Generator = realGenerator

}