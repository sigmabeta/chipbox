package net.sigmabeta.chipbox.js.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import net.sigmabeta.chipbox.browserstate.BrowserStateEvents
import net.sigmabeta.chipbox.browserstate.BrowserStateReason
import net.sigmabeta.chipbox.browserstate.StorageFavoritesRepository
import net.sigmabeta.chipbox.browserstate.StoragePlaybackHistoryRepository
import net.sigmabeta.chipbox.browserstate.StoragePlaybackSessionStore
import net.sigmabeta.chipbox.browserstate.StoragePlaylistsRepository
import net.sigmabeta.chipbox.browserstate.StorageSearchHistoryRepository
import net.sigmabeta.chipbox.browserstate.referencedTrackIds
import net.sigmabeta.chipbox.browserstate.withKnownTracks
import net.sigmabeta.chipbox.capabilities.AppCapabilities
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.crash.CrashReport
import net.sigmabeta.chipbox.crash.CrashReportStore
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.real.RealDebugSettingsManager
import net.sigmabeta.chipbox.debuginfo.DebugInfoManager
import net.sigmabeta.chipbox.debuginfo.real.RealDebugInfoManager
import net.sigmabeta.chipbox.favorites.FavoritesRepository
import net.sigmabeta.chipbox.features.folderpicker.StorageVolumeProvider
import net.sigmabeta.chipbox.features.home.module.ScanRefreshInterval
import net.sigmabeta.chipbox.history.PlaybackHistoryRecorder
import net.sigmabeta.chipbox.history.PlaybackHistoryRepository
import net.sigmabeta.chipbox.history.real.RealPlaybackHistoryRecorder
import net.sigmabeta.chipbox.js.WebBuildInfo
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
import net.sigmabeta.chipbox.js.inert.EmptyLibrarySource
import net.sigmabeta.chipbox.js.inert.NoopScanner
import net.sigmabeta.chipbox.notif.NotifManager
import net.sigmabeta.chipbox.notif.ScanFailureNotifRecorder
import net.sigmabeta.chipbox.notif.real.InsecureOriginNotifRecorder
import net.sigmabeta.chipbox.notif.real.RealNotifManager
import net.sigmabeta.chipbox.notif.real.RealScanFailureNotifRecorder
import net.sigmabeta.chipbox.js.repository.RemoteRepository
import net.sigmabeta.chipbox.js.speaker.WebAudioSpeaker
import net.sigmabeta.chipbox.js.storage.LocalStorageStorage
import net.sigmabeta.chipbox.player.buffer.BufferDebugSource
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.buffer.real.RealBufferManager
import net.sigmabeta.chipbox.player.director.Director
import net.sigmabeta.chipbox.player.director.real.RealDirector
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.player.generator.Generator
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.player.persistence.PlaybackSessionPersister
import net.sigmabeta.chipbox.player.persistence.PlaybackSessionStore
import net.sigmabeta.chipbox.player.persistence.SessionSnapshotFilter
import net.sigmabeta.chipbox.player.persistence.real.RealPlaybackSessionPersister
import net.sigmabeta.chipbox.player.resampler.CubicResampler
import net.sigmabeta.chipbox.player.resampler.LinearResampler
import net.sigmabeta.chipbox.player.resampler.Resampler
import net.sigmabeta.chipbox.player.speaker.Speaker
import net.sigmabeta.chipbox.playlists.PlaylistsRepository
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.chipbox.settings.ResamplerMode
import net.sigmabeta.chipbox.settings.real.RealChipboxSettingsManager
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.storage.common.Storage
import net.sigmabeta.sage.ui.StringProvider
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/**
 * Binding containers for the JS/browser target. The JS twin of `JvmModules.kt`:
 *
 * - **Repository** — [RemoteRepository] over an [HttpClient] (Ktor JS + JSON). Base URL derives
 *   from `window.location.origin`.
 * - **LibrarySource / ContentSource** — [EmptyLibrarySource] + [HttpContentSource]. The server owns
 *   the real library (one directory, scanned at startup); the browser can list no roots and adds
 *   none. Track bytes come from `/api/files/{id}`. Library administration is disabled by
 *   [AppCapabilities], and the inert source exists only to close the graph for shared view models.
 * - **Generator / Speaker / Director** — *real*. `RealGenerator` drives the nine WASM emulators
 *   ([WasmGmeEmulator], [WasmPsfEmulator], [WasmSsfEmulator], [WasmUsfEmulator], [WasmNcsfEmulator],
 *   [WasmTwosfEmulator], [WasmVgmEmulator], [WasmGbaEmulator], [WasmVgmstreamEmulator]) through the
 *   standard `RealPcmTrackSourceFactory` chain. Bytes round-trip through `FakeFileSystem` for
 *   staging — the emulator subclass reads the staged path back out into the WASM heap.
 *   `WebAudioSpeaker` pumps PCM to an AudioWorkletNode. `RealDirector` orchestrates the lot.
 * - **Scanner** — [NoopScanner] (inert; scans run on the server, not in the browser).
 * - **Settings / DebugSettings / DebugInfo** — real localStorage-backed managers + the real
 *   debug-info aggregator.
 * - **AppCapabilities** — the browser's policy: no library administration, no debug section, but
 *   the browser-local Error Log stays reachable. See [WebCapabilitiesModule].
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
    fun provideRepository(
        client: HttpClient,
        searchHistory: StorageSearchHistoryRepository,
    ): Repository = RemoteRepository(client, searchHistory)

    // Recent searches are browser state, not server state; the same versioned localStorage store
    // family as favorites/playlists keeps them durable and cross-tab-live.
    @Provides @SingleIn(AppScope::class)
    fun provideSearchHistory(
        storage: Storage,
        events: BrowserStateEvents,
    ): StorageSearchHistoryRepository = StorageSearchHistoryRepository(storage, events)
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebHomeConfigModule {
    // Throttle the Home scan-status card to a calm cadence; the scanner heartbeats per file.
    @Provides @SingleIn(AppScope::class)
    fun provideScanRefreshInterval(): ScanRefreshInterval = ScanRefreshInterval(ScanRefreshInterval.PRODUCTION_MILLIS)
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebContentSourceModule {
    // The browser exposes no library roots: the server owns the single configured directory and
    // scans it at startup. EmptyLibrarySource is an inert LibrarySource that keeps the shared view
    // models constructible; the UI never reaches it because AppCapabilities disables library
    // administration on web. The interesting ContentSource is [HttpContentSource], wired into the
    // registry with sourceId="file" so `BaseGenerator.loadNextTrack` resolves server-scanned tracks
    // (whose `track.source` is "file") and round-trips their bytes through `/api/files/{id}`.
    @Provides @SingleIn(AppScope::class)
    fun provideEmptyLibrarySource(): EmptyLibrarySource = EmptyLibrarySource()

    @Provides @SingleIn(AppScope::class)
    fun provideLibrarySource(impl: EmptyLibrarySource): LibrarySource = impl

    @Provides @SingleIn(AppScope::class)
    fun provideHttpContentSource(client: HttpClient): HttpContentSource = HttpContentSource(client)

    @Provides @SingleIn(AppScope::class)
    fun provideContentSourceRegistry(
        http: HttpContentSource,
    ): ContentSourceRegistry = ContentSourceRegistry(setOf<ContentSource>(http))
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
        resamplers: Map<ResamplerMode, Resampler>,
    ): WebAudioSpeaker = WebAudioSpeaker(bufferManager, hatchet, resamplerFor(resamplers))

    /**
     * The single-technique resampler the worklet should use, resolved once from the saved setting
     * (read synchronously from localStorage — no live switching). A worklet stream must always reach
     * the context rate, so OS maps to the linear kernel rather than bypassing.
     */
    private fun resamplerFor(resamplers: Map<ResamplerMode, Resampler>): Resampler? {
        val saved = window.localStorage.getItem(RealChipboxSettingsManager.KEY_RESAMPLER_MODE)
        val mode = ResamplerMode.fromStorageValue(saved)
        return resamplers[if (mode == ResamplerMode.CUBIC) ResamplerMode.CUBIC else ResamplerMode.LINEAR]
    }

    // The `:cbox:common:player:resampler:di` module is JVM-only (sage.jvm), so re-declare the
    // single-technique resampler map here for JS — same pattern as WebDirectorModule.
    @Provides @SingleIn(AppScope::class)
    fun provideResamplers(): Map<ResamplerMode, Resampler> = mapOf(
        ResamplerMode.LINEAR to LinearResampler(),
        ResamplerMode.CUBIC to CubicResampler(),
    )

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
        playlistsRepository: PlaylistsRepository,
        favoritesRepository: FavoritesRepository,
        settingsManager: ChipboxSettingsManager,
        hatchet: Hatchet,
    ): Director = RealDirector(
        generator,
        speaker,
        repository,
        playlistsRepository,
        favoritesRepository,
        settingsManager,
        hatchet,
    )
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebBrowserStateModule {
    // Favorites + playlists live in window.localStorage (there is no Room on web and no server-side
    // user state). `StorageFavoritesRepository` / `StoragePlaylistsRepository` are versioned,
    // cross-tab-live, and non-throwing; failures land in this event sink instead of the UI.
    @Provides @SingleIn(AppScope::class)
    fun provideBrowserStateEvents(hatchet: Hatchet): BrowserStateEvents = BrowserStateEvents { event ->
        when (event.reason) {
            BrowserStateReason.MISSING -> hatchet.d(event.logMessage)
            else -> hatchet.w(event.logMessage)
        }
    }

    @Provides @SingleIn(AppScope::class)
    fun provideFavoritesRepository(
        storage: Storage,
        events: BrowserStateEvents,
    ): FavoritesRepository = StorageFavoritesRepository(storage, events)

    @Provides @SingleIn(AppScope::class)
    fun providePlaylistsRepository(
        storage: Storage,
        events: BrowserStateEvents,
    ): PlaylistsRepository = StoragePlaylistsRepository(storage, events)
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebFolderPickerModule {
    // FolderPicker's JS Route is a no-op stub, but its assisted ViewModel factory (contributed via
    // appui's dependency on features/folder-picker/real) still needs this graph edge. The browser
    // has no mounted storage volumes to enumerate; desktop's JvmStorageVolumeProvider is JVM-only.
    @Provides @SingleIn(AppScope::class)
    fun provideStorageVolumeProvider(): StorageVolumeProvider = StorageVolumeProvider { emptyList() }
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebScannerModule {
    // Scans happen on the server, so the graph gets an inert scanner rather than a test fake.
    @Provides @SingleIn(AppScope::class)
    fun provideScanner(): Scanner = NoopScanner()
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebCapabilitiesModule {
    // The browser is a client of a self-hosted server: the library lifecycle (CHIPBOX_LIBRARY_DIR +
    // restart) is an admin concern, so no screen may add folders, rescan, or clear the library.
    // The debug section stays hidden even if a persisted five-tap flag is on; the Error Log remains
    // reachable from Settings > Diagnostics because it is browser/session-local.
    @Provides @SingleIn(AppScope::class)
    fun provideAppCapabilities(): AppCapabilities = AppCapabilities(
        canManageLibrary = false,
        showDebugSection = false,
        showErrorLog = true,
    )
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebHistoryModule {
    // Browser-local playback history. The browser has no Room store and the server exposes no
    // history endpoint, so plays live in window.localStorage through ticket 06's versioned,
    // bounded (500-play) store at `chipbox.playback_history`. It's fed by the shared, tested
    // [RealPlaybackHistoryRecorder] (the same recorder apps/jvm + apps/android use) — JsMain calls
    // observe() at startup, so once a track plays past the 10s / play-to-end threshold the Home
    // recently-/most-played cards fill and survive reloads. Reads derive from `savedStringFlow`,
    // so a play recorded in another tab appears without a reload.
    @Provides @SingleIn(AppScope::class)
    fun providePlaybackHistoryRepository(
        storage: Storage,
        events: BrowserStateEvents,
    ): PlaybackHistoryRepository = StoragePlaybackHistoryRepository(storage, events)

    @Provides @SingleIn(AppScope::class)
    fun providePlaybackHistoryRecorder(
        director: Director,
        repository: PlaybackHistoryRepository,
        hatchet: Hatchet,
    ): PlaybackHistoryRecorder = RealPlaybackHistoryRecorder(director, repository, hatchet)
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebPlaybackSessionModule {
    // The saved session (queue, current track, position, shuffle, repeat) lives in the same
    // versioned browser store. `StoragePlaybackSessionStore` implements the shared
    // PlaybackSessionStore contract over localStorage; the raw snapshot the shared
    // RealPlaybackSessionStore wrote is readable as schema version 0.
    @Provides @SingleIn(AppScope::class)
    fun providePlaybackSessionStore(
        storage: Storage,
        events: BrowserStateEvents,
    ): PlaybackSessionStore = StoragePlaybackSessionStore(storage, events)

    // The shared persister owns the save/restore semantics (save on PLAYING -> PAUSED, clear on
    // STOPPED, snapshotNow for teardown); the filter is the browser-only pre-restore step that
    // drops track ids a rebuilt library/RSN re-key no longer has. If the lookup itself fails we
    // keep the snapshot untouched — a transient server error must not erase a resumable session.
    @Provides @SingleIn(AppScope::class)
    fun providePlaybackSessionPersister(
        director: Director,
        store: PlaybackSessionStore,
        repository: Repository,
        hatchet: Hatchet,
    ): PlaybackSessionPersister = RealPlaybackSessionPersister(
        director = director,
        store = store,
        hatchet = hatchet,
        filter = SessionSnapshotFilter { snapshot ->
            val known = repository.knownTrackIds(snapshot.referencedTrackIds())
                ?: return@SessionSnapshotFilter snapshot
            snapshot.withKnownTracks(known)
        },
    )

    /**
     * Resolve [ids] to the tracks the library still has. Null means the lookup failed (API
     * unreachable, scan not ready) and the caller should leave the snapshot alone. Ids are looked
     * up in small chunks so a long queue (an all-tracks or playlist session) cannot overflow the
     * server's request line.
     */
    private suspend fun Repository.knownTrackIds(ids: List<Long>): Set<Long>? {
        if (ids.isEmpty()) return emptySet()
        val known = mutableSetOf<Long>()
        for (chunk in ids.distinct().chunked(TRACK_LOOKUP_CHUNK)) {
            val data = try {
                getTracksByIds(chunk).first { it !is Data.Loading }
            } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                return null
            }
            when (data) {
                is Data.Succeeded -> known += data.data.map { it.id }
                Data.Empty -> Unit
                is Data.Failed -> return null
                Data.Loading -> return null
            }
        }
        return known
    }

    private const val TRACK_LOOKUP_CHUNK = 100
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

    // The real store reads crash files off disk (jvmShared, no JS variant). The browser has no such
    // sink, so the crash-log screen sees an always-empty store rather than dragging in file I/O.
    @Provides @SingleIn(AppScope::class)
    fun provideCrashReportStore(): CrashReportStore = object : CrashReportStore {
        override fun list(): List<CrashReport> = emptyList()
        override fun clear() = Unit
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object WebNotifModule {
    // Home's notification row reads the shared RealNotifManager over localStorage; the scanner
    // failure recorder is inert here because the browser uses CountingScanner (server-side scans),
    // but wiring it keeps the graph shape identical across targets.
    @Provides @SingleIn(AppScope::class)
    fun provideNotifManager(
        storage: Storage,
        scope: CoroutineScope,
        hatchet: Hatchet,
    ): NotifManager = RealNotifManager(storage, scope, hatchet)

    @Provides @SingleIn(AppScope::class)
    fun provideScanFailureNotifRecorder(
        scanner: Scanner,
        notifManager: NotifManager,
        stringProvider: StringProvider,
        hatchet: Hatchet,
    ): ScanFailureNotifRecorder = RealScanFailureNotifRecorder(
        scanner = scanner,
        notifManager = notifManager,
        stringProvider = stringProvider,
        hatchet = hatchet,
    )

    @Provides @SingleIn(AppScope::class)
    fun provideInsecureOriginNotifRecorder(
        notifManager: NotifManager,
        stringProvider: StringProvider,
        hatchet: Hatchet,
    ): InsecureOriginNotifRecorder = InsecureOriginNotifRecorder(
        notifManager = notifManager,
        stringProvider = stringProvider,
        hatchet = hatchet,
        // AudioWorklet is secure-context-only; a plain-HTTP LAN page reports false here.
        isSecureContext = { window.asDynamic().isSecureContext as? Boolean ?: true },
    )
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
    // There is no BuildConfig on JS. The real version/branch/build time come from the generated
    // WebBuildInfo object (deterministic CI/Docker inputs, falling back to git locally, written by
    // :apps:js:generateWebBuildInfo). isDebug is false: the browser is a release-shaped client, so
    // the Settings debug section and the debug palette swap stay off.
    @Provides @SingleIn(AppScope::class)
    fun provideAppInfo(): AppInfo = AppInfo(
        isDebug = false,
        versionName = WebBuildInfo.VERSION_NAME,
        versionCode = WebBuildInfo.VERSION_CODE,
        buildTimeMs = WebBuildInfo.BUILD_TIME_MS,
        buildBranch = WebBuildInfo.BUILD_BRANCH,
    )
}
