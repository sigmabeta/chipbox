package net.sigmabeta.chipbox.uitest.harness

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.contentsource.fake.FakeLibrarySource
import net.sigmabeta.chipbox.crash.CrashReport
import net.sigmabeta.chipbox.crash.CrashReportStore
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.ImageLoaderSource
import net.sigmabeta.chipbox.debug.fake.FakeDebugSettingsManager
import net.sigmabeta.chipbox.debuginfo.DebugInfoManager
import net.sigmabeta.chipbox.debuginfo.fake.FakeDebugInfoManager
import net.sigmabeta.chipbox.favorites.FavoritesRepository
import net.sigmabeta.chipbox.favorites.fake.FakeFavoritesRepository
import net.sigmabeta.chipbox.history.PlaybackHistoryRepository
import net.sigmabeta.chipbox.history.fake.FakePlaybackHistoryRepository
import net.sigmabeta.chipbox.playlists.PlaylistsRepository
import net.sigmabeta.chipbox.playlists.fake.FakePlaylistsRepository
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.fake.FakeDirector
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.memory.MemoryRepository
import net.sigmabeta.chipbox.repository.memory.RandomMemoryRepository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.chipbox.settings.fake.FakeChipboxSettingsManager
import net.sigmabeta.chipbox.strings.real.ChipboxStringProvider
import net.sigmabeta.chipbox.strings.real.loadChipboxStrings
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.BasicHatchet
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
 *  - [StringProvider] → the real composeResources-backed [ChipboxStringProvider] (preloaded via
 *    [loadChipboxStrings], so screens render actual text and tests can assert on it); [Hatchet] → a
 *    real [BasicHatchet] (prints to stdout).
 *
 * Extends [ViewModelGraph] for the `metroViewModelFactory` accessor, and aggregates every
 * `@ContributesIntoMap` ViewModel + [TestMetroViewModelFactory] on the test classpath via
 * `@DependencyGraph(AppScope::class)`.
 */
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface TestAppGraph : ViewModelGraph {
    val memoryRepository: MemoryRepository

    /** The bound [Director] as a [FakeDirector], so the harness can assert on its recorded
     *  [requests][FakeDirector.requests]. */
    val fakeDirector: FakeDirector

    /** The bound [Scanner] as a [CountingScanner], so the harness can drive scan state/events that
     *  the scan-status Home card reacts to. */
    val countingScanner: CountingScanner

    /** The bound [FavoritesRepository] as a [FakeFavoritesRepository], so the harness can seed
     *  favorites before navigating to a screen that reads them. */
    val fakeFavoritesRepository: FakeFavoritesRepository

    /** The bound [PlaylistsRepository] as a [FakePlaylistsRepository], so the harness can seed
     *  playlists before navigating to a screen that reads them. */
    val fakePlaylistsRepository: FakePlaylistsRepository

    /** The shared logger — screens log through it, and the harness reuses it to announce where it
     *  wrote failure artifacts. A real [BasicHatchet] (prints to stdout), not a no-op stub. */
    val hatchet: Hatchet

    /** The real string provider, exposed so the shell can supply it as `LocalChipboxStringProvider`
     *  (the same instance the screens' ViewModels resolve through DI). */
    val stringProvider: StringProvider

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
    fun providePlaybackHistoryRepository(): PlaybackHistoryRepository = FakePlaybackHistoryRepository()

    @Provides
    @SingleIn(AppScope::class)
    fun provideFakeFavoritesRepository(): FakeFavoritesRepository = FakeFavoritesRepository()

    @Provides
    @SingleIn(AppScope::class)
    fun provideFavoritesRepository(fake: FakeFavoritesRepository): FavoritesRepository = fake

    @Provides
    @SingleIn(AppScope::class)
    fun provideFakePlaylistsRepository(): FakePlaylistsRepository = FakePlaylistsRepository()

    @Provides
    @SingleIn(AppScope::class)
    fun providePlaylistsRepository(fake: FakePlaylistsRepository): PlaylistsRepository = fake

    @Provides
    @SingleIn(AppScope::class)
    fun provideFakeDirector(): FakeDirector = FakeDirector()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDirector(fakeDirector: FakeDirector): Director = fakeDirector

    // Preload all strings once (suspend → runBlocking), exactly like the production apps. The lookup
    // is then synchronous and happens outside composition, so runComposeUiTest never has to resolve a
    // composeResource mid-render (which the old empty-string stub existed to avoid).
    @Provides
    @SingleIn(AppScope::class)
    fun provideStringProvider(): StringProvider = runBlocking { ChipboxStringProvider(loadChipboxStrings()) }

    @Provides
    @SingleIn(AppScope::class)
    fun provideHatchet(): Hatchet = BasicHatchet()

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

    // Unconfined rather than the default Dispatchers.Main: the UI harness never calls
    // Dispatchers.setMain, so a Main-confined scanner scope would throw the moment clearScan()
    // (tap-to-dismiss) launches on it.
    @Provides
    @SingleIn(AppScope::class)
    fun provideCountingScanner(): CountingScanner = CountingScanner(Dispatchers.Unconfined)

    @Provides
    @SingleIn(AppScope::class)
    fun provideScanner(scanner: CountingScanner): Scanner = scanner

    // Tests default to the fake image loader, so screens render deterministic generated gradients
    // instead of fetching cover art through Coil. The shell reads this and provides LocalForceFakeImages.
    @Provides
    @SingleIn(AppScope::class)
    fun provideDebugSettingsManager(): DebugSettingsManager =
        FakeDebugSettingsManager(initialImageLoaderSource = ImageLoaderSource.FAKE)

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
