package net.sigmabeta.chipbox.player.generator.fake.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.player.generator.fake.FakeGenerator
import net.sigmabeta.chipbox.repository.Repository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FakeGeneratorModule {
    @Provides
    @Singleton
    internal fun provideFakeGenerator(
        repository: Repository
    ) = FakeGenerator(repository)
}