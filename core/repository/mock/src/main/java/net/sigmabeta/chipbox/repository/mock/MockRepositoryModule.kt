package net.sigmabeta.chipbox.repository.mock

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sigmabeta.chipbox.repository.Repository
import java.util.*
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MockRepositoryModule {
    @Provides
    @Singleton
    @Named("RngSeed")
    internal fun provideSeed() = SEED_RANDOM_NUMBER_GENERATOR

    @Provides
    @Singleton
    internal fun provideRandom(@Named("RngSeed") seed: Long) = Random(seed)

    @Provides
    @Singleton
    fun provideMockRepository(
        random: Random,
        @Named("RngSeed") seed: Long,
        stringGenerator: StringGenerator
    ): Repository = MockRepository(
        random,
        seed,
        stringGenerator
    )

    const val SEED_RANDOM_NUMBER_GENERATOR = 123456L
}