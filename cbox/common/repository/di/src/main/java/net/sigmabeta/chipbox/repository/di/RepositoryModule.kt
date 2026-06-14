package net.sigmabeta.chipbox.repository.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import net.sigmabeta.chipbox.repository.memory.MemoryRepository
import net.sigmabeta.chipbox.repository.memory.RandomMemoryRepository
import net.sigmabeta.sage.di.AppScope

@BindingContainer
@ContributesTo(AppScope::class)
object RepositoryModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideMemoryRepository(): MemoryRepository = MemoryRepository()

    // The "Random" debug repository source. Lazy: only built if a platform's Repository binding
    // actually selects it (see the repository-source switch in the platform DI modules).
    @Provides
    @SingleIn(AppScope::class)
    fun provideRandomMemoryRepository(): RandomMemoryRepository = RandomMemoryRepository()
}
