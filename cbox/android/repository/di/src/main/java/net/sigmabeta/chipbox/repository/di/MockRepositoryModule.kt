package net.sigmabeta.chipbox.repository.di

import android.content.Context
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.util.Random
import net.sigmabeta.chipbox.repository.mock.MockImageUrlGenerator
import net.sigmabeta.chipbox.repository.mock.MockRepository
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringGenerator

@BindingContainer
@ContributesTo(AppScope::class)
object MockRepositoryModule {
    @Provides
    @SingleIn(AppScope::class)
    @Named("RngSeed")
    fun provideSeed(): Long = SEED_RANDOM_NUMBER_GENERATOR

    @Provides
    @SingleIn(AppScope::class)
    fun provideRandom(@Named("RngSeed") seed: Long): Random = Random(seed)

    @Provides
    @SingleIn(AppScope::class)
    fun provideStringGenerator(random: Random): StringGenerator = StringGenerator(random)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMockImageUrlGenerator(context: Context): MockImageUrlGenerator =
        MockImageUrlGenerator(context)

    @Provides
    @SingleIn(AppScope::class)
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
