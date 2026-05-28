package net.sigmabeta.chipbox.js.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.contentsource.fake.FakeLibrarySource
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.fake.FakeDebugSettingsManager
import net.sigmabeta.chipbox.debuginfo.DebugInfoManager
import net.sigmabeta.chipbox.debuginfo.fake.FakeDebugInfoManager
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.fake.FakeDirector
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.generator.fake.FakeGenerator
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.player.speaker.fake.FakeSpeaker
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.memory.MemoryRepository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.chipbox.settings.fake.FakeChipboxSettingsManager
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.di.AppScope
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem

/**
 * Binding containers for the JS/browser target. The JS twin of `JvmModules.kt`, but every
 * platform-dependent subsystem is wired to a fake:
 *
 * - **Repository** — `MemoryRepository` (in-memory; empty library on first launch, no
 *   persistence across page reloads).
 * - **LibrarySource / ContentSource** — `FakeLibrarySource` (no filesystem in the browser).
 * - **Speaker / Generator / Director** — fakes; the real chain depends on JNI C++ emulators.
 *   The UI renders and reacts to playback state changes, but no audio is produced.
 * - **Scanner** — `CountingScanner` (no-op scan; nothing to walk).
 * - **Settings / DebugSettings / DebugInfo** — fakes with default values; no persistence.
 *
 * `Hatchet` and `StringProvider` enter the graph via `WebChipboxGraph.Factory` (supplied at
 * startup from `JsMain.kt`) — no `@Provides` for them here.
 */

@BindingContainer
@ContributesTo(AppScope::class)
object WebRepositoryModule {
    @Provides @SingleIn(AppScope::class)
    fun provideRepository(): Repository = MemoryRepository()
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebContentSourceModule {
    @Provides @SingleIn(AppScope::class)
    fun provideFakeLibrarySource(): FakeLibrarySource = FakeLibrarySource()

    @Provides @SingleIn(AppScope::class)
    fun provideLibrarySource(impl: FakeLibrarySource): LibrarySource = impl

    @Provides @SingleIn(AppScope::class)
    fun provideContentSourceRegistry(impl: FakeLibrarySource): ContentSourceRegistry =
        ContentSourceRegistry(setOf<ContentSource>(impl))
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebPlayerModule {
    @Provides @SingleIn(AppScope::class)
    fun provideSpeaker(): Speaker = FakeSpeaker()

    @Provides @SingleIn(AppScope::class)
    fun provideGenerator(): Generator = FakeGenerator()

    @Provides @SingleIn(AppScope::class)
    fun provideDirector(): Director = FakeDirector()
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebScannerModule {
    @Provides @SingleIn(AppScope::class)
    fun provideScanner(): Scanner = CountingScanner()
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebDebugInfoModule {
    @Provides @SingleIn(AppScope::class)
    fun provideDebugInfoManager(): DebugInfoManager = FakeDebugInfoManager()
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebSettingsModule {
    @Provides @SingleIn(AppScope::class)
    fun provideChipboxSettingsManager(): ChipboxSettingsManager = FakeChipboxSettingsManager()

    @Provides @SingleIn(AppScope::class)
    fun provideDebugSettingsManager(): DebugSettingsManager = FakeDebugSettingsManager()
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebFileSystemModule {
    // FolderPicker's OkioFolderLister @Inject ctor takes a FileSystem. On the browser there's no
    // real filesystem; FakeFileSystem keeps the binding closed. The JS folder picker Route is a
    // jsMain stub that renders nothing, so the lister never actually runs.
    @Provides @SingleIn(AppScope::class)
    fun provideFileSystem(): FileSystem = FakeFileSystem()
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebCoroutinesModule {
    // Mirrors JvmCoroutinesModule — RealDebugInfoManager and a few other singletons take a
    // CoroutineScope. SupervisorJob keeps one failing child from cancelling siblings.
    @Provides @SingleIn(AppScope::class)
    fun provideCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebAppInfoModule {
    // No BuildConfig on JS, so the values are intentionally fake — good enough for the
    // Settings screen's "About" entries.
    @Provides @SingleIn(AppScope::class)
    fun provideAppInfo(): AppInfo = AppInfo(
        isDebug = true,
        versionName = "0.1.0-js",
        versionCode = 1,
        buildTimeMs = 0L,
        buildBranch = "web",
    )
}
