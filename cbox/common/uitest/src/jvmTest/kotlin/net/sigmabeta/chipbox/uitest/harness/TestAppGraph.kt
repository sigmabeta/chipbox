package net.sigmabeta.chipbox.uitest.harness

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.fake.FakeDirector
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.memory.MemoryRepository
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

/**
 * Cross-platform Metro test graph for the UI harness. Provides the leaf bindings a real screen's
 * ViewModel needs — backed by fakes so the suite is deterministic and free of I/O (no real DB,
 * audio, or filesystem):
 *
 *  - [Repository] → a seedable in-memory [MemoryRepository] (exposed as [memoryRepository] so the
 *    harness can `upsertGame(...)` before navigating).
 *  - [Director] → [FakeDirector] (records calls; real playback isn't needed to render/assert).
 *  - [StringProvider] / [Hatchet] → inert stubs.
 *
 * Extends [ViewModelGraph] for the `metroViewModelFactory` accessor, and aggregates every
 * `@ContributesIntoMap` ViewModel + [TestMetroViewModelFactory] on the test classpath via
 * `@DependencyGraph(AppScope::class)`.
 */
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface TestAppGraph : ViewModelGraph {
    val memoryRepository: MemoryRepository

    @Provides
    @SingleIn(AppScope::class)
    fun provideMemoryRepository(): MemoryRepository = MemoryRepository()

    @Provides
    @SingleIn(AppScope::class)
    fun provideRepository(memoryRepository: MemoryRepository): Repository = memoryRepository

    @Provides
    @SingleIn(AppScope::class)
    fun provideDirector(): Director = FakeDirector()

    @Provides
    @SingleIn(AppScope::class)
    fun provideStringProvider(): StringProvider = StubStringProvider

    @Provides
    @SingleIn(AppScope::class)
    fun provideHatchet(): Hatchet = StubHatchet

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(): TestAppGraph
    }
}

/** Builds a fresh [TestAppGraph] — one per test, so seeded state never leaks between cases. */
fun createTestAppGraph(): TestAppGraph = createGraphFactory<TestAppGraph.Factory>().create()
