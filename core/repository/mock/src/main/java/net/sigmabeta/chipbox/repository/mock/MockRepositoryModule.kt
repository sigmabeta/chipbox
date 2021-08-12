package net.sigmabeta.chipbox.repository.mock

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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

//    @Provides
//    @Singleton
//    fun provideRepository(
//        random: Random,
//        @Named("RngSeed") seed: Long,
//        stringGenerator: StringGenerator,
//        mockImageUrlGenerator: MockImageUrlGenerator
//    ): Repository = provideMockRepository(
//        random,
//        seed,
//        stringGenerator,
//        mockImageUrlGenerator
//    )

    @Provides
    @Singleton
    fun provideMockRepository(
        random: Random,
        @Named("RngSeed") seed: Long,
        stringGenerator: StringGenerator,
        mockImageUrlGenerator: MockImageUrlGenerator
    ) = MockRepository(
        random,
        seed,
        stringGenerator,
        mockImageUrlGenerator
    )

    private const val SEED_RANDOM_NUMBER_GENERATOR = 12345L
}