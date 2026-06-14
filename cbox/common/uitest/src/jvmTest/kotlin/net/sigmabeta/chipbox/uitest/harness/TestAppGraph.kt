package net.sigmabeta.chipbox.uitest.harness

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.contentsource.fake.FakeLibrarySource
import net.sigmabeta.chipbox.crash.CrashReport
import net.sigmabeta.chipbox.crash.CrashReportStore
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.fake.FakeDebugSettingsManager
import net.sigmabeta.chipbox.debuginfo.DebugInfoManager
import net.sigmabeta.chipbox.debuginfo.fake.FakeDebugInfoManager
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.fake.FakeDirector
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.memory.MemoryRepository
import net.sigmabeta.chipbox.repository.memory.RandomMemoryRepository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.chipbox.settings.fake.FakeChipboxSettingsManager
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem

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

    // Default to a deterministic, pre-populated library so hosted tabs/lists have content out of
    // the box (seed 1234 → same 10 games / 50 tracks / 5 artists every run). Tests can still
    // `upsertGame(...)` extra fixtures on top via the `memoryRepository` accessor.
    @Provides
    @SingleIn(AppScope::class)
    fun provideMemoryRepository(): MemoryRepository = RandomMemoryRepository()

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

    @Provides
    @SingleIn(AppScope::class)
    fun provideSettingsManager(): ChipboxSettingsManager = FakeChipboxSettingsManager()

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppInfo(): AppInfo = AppInfo(
        isDebug = true,
        versionName = "uitest",
        versionCode = 1,
        buildTimeMs = null,
        buildBranch = "uitest",
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideLibrarySource(): LibrarySource = FakeLibrarySource()

    @Provides
    @SingleIn(AppScope::class)
    fun provideScanner(): Scanner = CountingScanner()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDebugSettingsManager(): DebugSettingsManager = FakeDebugSettingsManager()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDebugInfoManager(): DebugInfoManager = FakeDebugInfoManager()

    @Provides
    @SingleIn(AppScope::class)
    fun provideCrashReportStore(): CrashReportStore = object : CrashReportStore {
        override fun list(): List<CrashReport> = emptyList()
        override fun clear() = Unit
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideFileSystem(): FileSystem = FakeFileSystem()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(): TestAppGraph
    }
}

/** Builds a fresh [TestAppGraph] — one per test, so seeded state never leaks between cases. */
fun createTestAppGraph(): TestAppGraph = createGraphFactory<TestAppGraph.Factory>().create()
