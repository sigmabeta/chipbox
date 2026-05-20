package net.sigmabeta.chipbox.repository.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.zacsweers.metro.ContributesTo
import net.sigmabeta.chipbox.repository.mock.MockImageUrlGenerator
import net.sigmabeta.chipbox.repository.mock.MockRepository
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringGenerator
import java.util.Random
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(AppScope::class)
object MockRepositoryModule {
    @Provides
    @Singleton
    @Named("RngSeed")
    fun provideSeed(): Long = SEED_RANDOM_NUMBER_GENERATOR

    @Provides
    @Singleton
    fun provideRandom(@Named("RngSeed") seed: Long): Random = Random(seed)

    @Provides
    @Singleton
    fun provideStringGenerator(random: Random): StringGenerator = StringGenerator(random)

    @Provides
    @Singleton
    fun provideMockImageUrlGenerator(@ApplicationContext context: Context): MockImageUrlGenerator =
        MockImageUrlGenerator(context)

    @Provides
    @Singleton
    fun provideMockRepository(
        random: Random,
        @Named("RngSeed") seed: Long,
        stringGenerator: StringGenerator,
        mockImageUrlGenerator: MockImageUrlGenerator,
        hatchet: Hatchet,
    ): MockRepository = MockRepository(
        random,
        seed,
        stringGenerator,
        mockImageUrlGenerator,
        hatchet,
    )

    private const val SEED_RANDOM_NUMBER_GENERATOR = 12345L
}
