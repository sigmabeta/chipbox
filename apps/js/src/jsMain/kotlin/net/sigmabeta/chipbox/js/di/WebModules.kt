package net.sigmabeta.chipbox.js.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.contentsource.fake.FakeLibrarySource
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.real.RealDebugSettingsManager
import net.sigmabeta.chipbox.debuginfo.DebugInfoManager
import net.sigmabeta.chipbox.debuginfo.real.RealDebugInfoManager
import net.sigmabeta.chipbox.js.contentsource.HttpContentSource
import net.sigmabeta.chipbox.js.emulators.WasmGbaEmulator
import net.sigmabeta.chipbox.js.emulators.WasmGmeEmulator
import net.sigmabeta.chipbox.js.emulators.WasmNcsfEmulator
import net.sigmabeta.chipbox.js.emulators.WasmPsfEmulator
import net.sigmabeta.chipbox.js.emulators.WasmSsfEmulator
import net.sigmabeta.chipbox.js.emulators.WasmTwosfEmulator
import net.sigmabeta.chipbox.js.emulators.WasmUsfEmulator
import net.sigmabeta.chipbox.js.emulators.WasmVgmEmulator
import net.sigmabeta.chipbox.js.emulators.WasmVgmstreamEmulator
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.js.repository.RemoteRepository
import net.sigmabeta.chipbox.js.speaker.WebAudioSpeaker
import net.sigmabeta.chipbox.player.buffer.BufferDebugSource
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.buffer.real.RealBufferManager
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.real.RealDirector
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.chipbox.js.storage.LocalStorageStorage
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.chipbox.settings.real.RealChipboxSettingsManager
import net.sigmabeta.sage.storage.common.Storage
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/**
 * Binding containers for the JS/browser target. The JS twin of `JvmModules.kt`:
 *
 * - **Repository** — [RemoteRepository] over an [HttpClient] (Ktor JS + JSON). Base URL derives
 *   from `window.location.origin`.
 * - **LibrarySource / ContentSource** — `FakeLibrarySource`. The server owns the real one; the
 *   UI's "manage library" screen can't add/remove server-side folders from the browser.
 * - **Generator / Speaker / Director** — *real*. `RealGenerator` drives WASM emulators
 *   ([WasmGmeEmulator] for libgme today; more coming) through the standard
 *   `RealPcmTrackSourceFactory` chain. Bytes round-trip through `FakeFileSystem` for staging —
 *   the emulator subclass reads the staged path back out into the WASM heap. `WebAudioSpeaker`
 *   pumps PCM to an AudioWorkletNode. `RealDirector` orchestrates the lot.
 * - **Scanner** — `CountingScanner` (no-op; scans run on the server, not in the browser).
 * - **Settings / DebugSettings / DebugInfo** — fakes; no client-side persistence in v1.
 *
 * `Hatchet` and `StringProvider` enter the graph via `WebChipboxGraph.Factory` (supplied at
 * startup from `JsMain.kt`) — no `@Provides` for them here.
 */

@BindingContainer
@ContributesTo(AppScope::class)
object WebRepositoryModule {
    @Provides @SingleIn(AppScope::class)
    fun provideHttpClient(): HttpClient = HttpClient(Js) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Provides @SingleIn(AppScope::class)
    fun provideRepository(client: HttpClient): Repository = RemoteRepository(client)
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebContentSourceModule {
    // FakeLibrarySource is the `LibrarySource` binding (used by ManageLibrary's UI to list +
    // add/remove folder roots) — the browser has no way to surface real server-side roots, so
    // the UI shows an empty list. The interesting ContentSource is [HttpContentSource], wired
    // into the registry with sourceId="file" so `BaseGenerator.loadNextTrack` resolves
    // server-scanned tracks (whose `track.source` is "file") and round-trips their bytes
    // through `/api/files/by-path`.
    @Provides @SingleIn(AppScope::class)
    fun provideFakeLibrarySource(): FakeLibrarySource = FakeLibrarySource()

    @Provides @SingleIn(AppScope::class)
    fun provideLibrarySource(impl: FakeLibrarySource): LibrarySource = impl

    @Provides @SingleIn(AppScope::class)
    fun provideHttpContentSource(client: HttpClient): HttpContentSource = HttpContentSource(client)

    @Provides @SingleIn(AppScope::class)
    fun provideContentSourceRegistry(
        fake: FakeLibrarySource,
        http: HttpContentSource,
    ): ContentSourceRegistry = ContentSourceRegistry(setOf<ContentSource>(fake, http))
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebBufferModule {
    @Provides @SingleIn(AppScope::class)
    fun provideRealBufferManager(hatchet: Hatchet): RealBufferManager = RealBufferManager(hatchet)

    @Provides @SingleIn(AppScope::class)
    fun provideProducer(impl: RealBufferManager): ProducerBufferManager = impl

    @Provides @SingleIn(AppScope::class)
    fun provideConsumer(impl: RealBufferManager): ConsumerBufferManager = impl

    // Plumbed for whoever ends up wanting buffer diagnostics on the JS PlaybackStatus screen.
    // Currently unused (no DebugInfoManager wired here), but the binding closes the graph.
    @Provides @SingleIn(AppScope::class)
    fun provideBufferDebugSource(impl: RealBufferManager): BufferDebugSource = impl
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebEmulatorsModule {
    // Each chiptune emulator is one WASM module + one Emulator subclass; the List<Emulator>
    // shape mirrors the JVM EmulatorProvider, so new ports just append here. JsMain's
    // pre-startup `loadChipboxXxx()` resolves each module before the chain hands an Emulator
    // its first track.
    @Provides @SingleIn(AppScope::class)
    fun provideWasmGmeEmulator(fileSystem: FileSystem): WasmGmeEmulator = WasmGmeEmulator(fileSystem)

    @Provides @SingleIn(AppScope::class)
    fun provideWasmVgmEmulator(fileSystem: FileSystem): WasmVgmEmulator = WasmVgmEmulator(fileSystem)

    @Provides @SingleIn(AppScope::class)
    fun provideWasmSsfEmulator(fileSystem: FileSystem): WasmSsfEmulator = WasmSsfEmulator(fileSystem)

    @Provides @SingleIn(AppScope::class)
    fun provideWasmUsfEmulator(fileSystem: FileSystem): WasmUsfEmulator = WasmUsfEmulator(fileSystem)

    @Provides @SingleIn(AppScope::class)
    fun provideWasmPsfEmulator(fileSystem: FileSystem): WasmPsfEmulator = WasmPsfEmulator(fileSystem)

    @Provides @SingleIn(AppScope::class)
    fun provideWasmNcsfEmulator(fileSystem: FileSystem): WasmNcsfEmulator = WasmNcsfEmulator(fileSystem)

    @Provides @SingleIn(AppScope::class)
    fun provideWasmTwosfEmulator(fileSystem: FileSystem): WasmTwosfEmulator = WasmTwosfEmulator(fileSystem)

    @Provides @SingleIn(AppScope::class)
    fun provideWasmVgmstreamEmulator(fileSystem: FileSystem): WasmVgmstreamEmulator = WasmVgmstreamEmulator(fileSystem)

    @Provides @SingleIn(AppScope::class)
    fun provideWasmGbaEmulator(fileSystem: FileSystem): WasmGbaEmulator = WasmGbaEmulator(fileSystem)

    @Provides @SingleIn(AppScope::class)
    fun provideEmulators(
        gme: WasmGmeEmulator,
        vgm: WasmVgmEmulator,
        ssf: WasmSsfEmulator,
        usf: WasmUsfEmulator,
        psf: WasmPsfEmulator,
        ncsf: WasmNcsfEmulator,
        twosf: WasmTwosfEmulator,
        gba: WasmGbaEmulator,
        vgmstream: WasmVgmstreamEmulator,
    ): List<Emulator> = listOf<Emulator>(gme, vgm, ssf, usf, psf, ncsf, twosf, gba, vgmstream)
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebGeneratorModule {
    // Reuses [RealGenerator] verbatim (same as JVM + Android). The cache layer enables seek
    // (live emulators can't fast-forward; the cache holds rendered PCM the speaker can rewind
    // into) and idempotent re-plays. Both `stagingDir` and `pcmCacheDir` are paths into the
    // `FakeFileSystem` from `WebFileSystemModule` — in-memory, doesn't persist across reloads,
    // which is fine: every browser session restages and re-renders.
    @Provides @SingleIn(AppScope::class)
    fun provideRealGenerator(
        repository: Repository,
        contentSources: ContentSourceRegistry,
        bufferManager: ProducerBufferManager,
        emulators: List<Emulator>,
        fileSystem: FileSystem,
        hatchet: Hatchet,
    ): RealGenerator {
        // Paths are arbitrary keys into the FakeFS map but must be valid POSIX paths and the
        // parents must exist (FakeFS doesn't auto-mkdir at write time).
        val stagingDir = "/chipbox/staging".toPath()
        val pcmCacheDir = "/chipbox/pcm-cache".toPath()
        fileSystem.createDirectories(stagingDir)
        fileSystem.createDirectories(pcmCacheDir)
        return RealGenerator(
            repository = repository,
            contentSourceRegistry = contentSources,
            bufferManager = bufferManager,
            emulators = emulators,
            stagingDir = stagingDir,
            pcmCacheDir = pcmCacheDir,
            fileSystem = fileSystem,
            hatchet = hatchet,
        )
    }

    @Provides @SingleIn(AppScope::class)
    fun provideGenerator(impl: RealGenerator): Generator = impl
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebSpeakerModule {
    @Provides @SingleIn(AppScope::class)
    fun provideWebAudioSpeaker(
        bufferManager: ConsumerBufferManager,
        hatchet: Hatchet,
        settingsManager: ChipboxSettingsManager,
    ): WebAudioSpeaker = WebAudioSpeaker(bufferManager, hatchet, settingsManager.getResamplerMode())

    @Provides @SingleIn(AppScope::class)
    fun provideSpeaker(impl: WebAudioSpeaker): Speaker = impl
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebDirectorModule {
    // The `:cbox:common:player:director:di` module that the JVM/Android apps use is `sage.jvm`
    // (JVM-only), so we re-declare the binding here for JS.
    @Provides @SingleIn(AppScope::class)
    fun provideDirector(
        generator: Generator,
        speaker: Speaker,
        repository: Repository,
        hatchet: Hatchet,
    ): Director = RealDirector(generator, speaker, repository, hatchet)
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
    // [RealDebugInfoManager] combines Director + Generator + Speaker + BufferDebugSource flows
    // into one PlaybackDebugInfo state. Every dep is already wired (see modules above);
    // the JS app just needs to bind the real impl instead of the fake.
    @Provides @SingleIn(AppScope::class)
    fun provideDebugInfoManager(
        director: Director,
        generator: Generator,
        speaker: Speaker,
        bufferDebugSource: BufferDebugSource,
        scope: CoroutineScope,
    ): DebugInfoManager = RealDebugInfoManager(
        director = director,
        generator = generator,
        speaker = speaker,
        bufferDebugSource = bufferDebugSource,
        scope = scope,
    )
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebSettingsModule {
    // Browser settings persist via `window.localStorage` — the commonMain `RealChipboxSettingsManager`
    // / `RealDebugSettingsManager` work against any `Storage` impl; the JS-specific piece is
    // just the [LocalStorageStorage] adapter.
    @Provides @SingleIn(AppScope::class)
    fun provideStorage(): Storage = LocalStorageStorage()

    @Provides @SingleIn(AppScope::class)
    fun provideChipboxSettingsManager(storage: Storage): ChipboxSettingsManager =
        RealChipboxSettingsManager(storage)

    @Provides @SingleIn(AppScope::class)
    fun provideDebugSettingsManager(storage: Storage): DebugSettingsManager =
        RealDebugSettingsManager(storage)
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebFileSystemModule {
    // FolderPicker's OkioFolderLister AND the player chain (RealGenerator's input staging,
    // PcmCache) both inject FileSystem. There's no real filesystem in the browser — FakeFileSystem
    // serves as an in-memory shim so the okio-based staging code works unchanged. Single
    // singleton so staging writes from RealGenerator and reads from WasmGmeEmulator hit the
    // same FS instance.
    //
    // Every "concurrent access" flag is enabled so `CachingPcmSource`'s render-ahead pattern
    // works the same way it does against a real filesystem:
    //  - reads + writes overlap: the consumer reads frames as the writer produces them.
    //  - atomic rename happens while the reader still holds the `.tmp` open: the writer
    //    completes ahead of playback and renames `<key>.pcm.tmp` → `<key>.pcm` mid-read.
    //  - delete-on-cleanup may race the reader holding the staging file or cache `.tmp`.
    //  - clobbering: not strictly needed today, enabled defensively.
    // The real filesystems used by JVM/Android allow all of these natively; FakeFileSystem
    // gates them behind individual flags so test code can opt into stricter checking.
    @Provides @SingleIn(AppScope::class)
    fun provideFileSystem(): FileSystem = FakeFileSystem().apply {
        allowWritesWhileWriting = true
        allowReadsWhileWriting = true
        allowMovingOpenFiles = true
        allowDeletingOpenFiles = true
        allowClobberingEmptyDirectories = true
    }
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
