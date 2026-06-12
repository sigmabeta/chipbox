package net.sigmabeta.chipbox.jvm.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.crash.CrashReporter
import net.sigmabeta.chipbox.crash.CrashReportStore
import net.sigmabeta.chipbox.crash.real.RealCrashReporter
import net.sigmabeta.chipbox.crash.real.RealCrashReportStore
import net.sigmabeta.chipbox.database.ChipboxDatabase
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.real.RealDebugSettingsManager
import net.sigmabeta.chipbox.jvm.JvmStorage
import net.sigmabeta.chipbox.jvm.logging.JvmHatchet
import net.sigmabeta.chipbox.contentsource.LocalFileContentSource
import net.sigmabeta.chipbox.player.resampler.Resampler
import net.sigmabeta.chipbox.player.speaker.real.SourceDataLineSpeaker
import net.sigmabeta.chipbox.strings.real.ChipboxStringProvider
import net.sigmabeta.chipbox.strings.real.loadChipboxStrings
import net.sigmabeta.chipbox.player.buffer.BufferDebugSource
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.buffer.real.RealBufferManager
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.player.emulators.EmulatorProvider
import net.sigmabeta.chipbox.player.emulators.vgmstream.VgmstreamProbe
import net.sigmabeta.chipbox.player.emulators.gba.GbaEmulator
import net.sigmabeta.chipbox.player.emulators.gme.GmeEmulator
import net.sigmabeta.chipbox.player.emulators.ncsf.NcsfEmulator
import net.sigmabeta.chipbox.player.emulators.psf.PsfEmulator
import net.sigmabeta.chipbox.player.emulators.ssf.SsfEmulator
import net.sigmabeta.chipbox.player.emulators.twosf.TwosfEmulator
import net.sigmabeta.chipbox.player.emulators.usf.UsfEmulator
import net.sigmabeta.chipbox.player.emulators.vgm.VgmEmulator
import net.sigmabeta.chipbox.player.emulators.vgmstream.VgmstreamEmulator
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.database.DatabaseRepository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.real.RealScanner
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

/**
 * Plain-Dagger modules for the headless JVM target. These mirror the Hilt `@Module` classes
 * under `cbox/.../di` shape-for-shape, but live in `apps/jvm` because the JVM has different
 * wiring needs: a JDBC SQLite driver instead of Context-backed Room, a file walker instead
 * of SAF, a CLI-supplied output dir instead of `Environment.getExternalStorageDirectory()`.
 *
 * Hilt itself is Android-only by design — the cbox `:di` modules are sage.android and apply
 * Hilt's Android plugin. Duplicating the trivial emulator/buffer providers here is simpler
 * than dragging those sage.android modules into a sage.jvm app's classpath; the duplication
 * is a few lines per binding.
 *
 * Caller-supplied paths (db file, render-cache workdir, WAV output dir) come into the graph
 * via `@BindsInstance` on [JvmChipboxComponent.Builder].
 */

@BindingContainer
@ContributesTo(AppScope::class)
object HatchetModule {
    // JvmHatchet mirrors AndroidHatchet (caller-derived tag, Thr/Msg body, Log.* severity ints).
    // Routed through stdout/stderr instead of logcat.
    @Provides @SingleIn(AppScope::class) fun provideHatchet(): Hatchet = JvmHatchet()
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmStringsModule {
    // Single multiplatform source: cbox/common/strings/real's composeResources, preloaded once.
    @Provides @SingleIn(AppScope::class)
    fun provideStringProvider(): StringProvider = runBlocking { ChipboxStringProvider(loadChipboxStrings()) }
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmDatabaseModule {
    @Provides @SingleIn(AppScope::class)
    fun provideDatabase(@Named("dbPath") path: String): ChipboxDatabase = Room
        .databaseBuilder<ChipboxDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        // The library is a derived cache; on a schema bump just rebuild it on the next scan rather
        // than ship migrations. Matches the Android builder.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmRepositoryModule {
    @Provides @SingleIn(AppScope::class)
    fun provideDatabaseRepository(database: ChipboxDatabase, hatchet: Hatchet): DatabaseRepository =
        DatabaseRepository(
            database.artistDao(),
            database.gameDao(),
            database.trackDao(),
            database.gameArtistDao(),
            database.trackArtistDao(),
            database.searchHistoryDao(),
            hatchet,
        )

    @Provides @SingleIn(AppScope::class)
    fun provideRepository(impl: DatabaseRepository): Repository = impl
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmContentSourceModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideLocalFileContentSource(@Named("workDir") workDir: File): LocalFileContentSource =
        LocalFileContentSource(File(workDir, "library-locations.txt"))

    @Provides @SingleIn(AppScope::class)
    fun provideLibrarySource(impl: LocalFileContentSource): LibrarySource = impl

    @Provides @SingleIn(AppScope::class)
    fun provideContentSourceRegistry(impl: LocalFileContentSource): ContentSourceRegistry =
        ContentSourceRegistry(setOf<ContentSource>(impl))
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmFileSystemModule {
    // FolderPicker's OkioFolderLister @Inject ctor takes a FileSystem; pin it to the live
    // platform filesystem. Other call sites that need a FileSystem (PCM cache, FileSpeaker,
    // …) currently pass FileSystem.SYSTEM inline in their @Provides — this is the first one
    // that injects it as a graph type.
    @Provides @SingleIn(AppScope::class)
    fun provideFileSystem(): FileSystem = FileSystem.SYSTEM
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmBufferModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideRealBufferManager(hatchet: Hatchet): RealBufferManager = RealBufferManager(hatchet)

    @Provides @SingleIn(AppScope::class)
    fun provideProducer(impl: RealBufferManager): ProducerBufferManager = impl

    @Provides @SingleIn(AppScope::class)
    fun provideConsumer(impl: RealBufferManager): ConsumerBufferManager = impl

    // RealDebugInfoManager (DebugInfoModule, pulled in via :debug-info:di) reads buffer
    // diagnostics through this — the Android side binds it the same way in BufferModule.
    @Provides @SingleIn(AppScope::class)
    fun provideBufferDebugSource(impl: RealBufferManager): BufferDebugSource = impl
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmCoroutinesModule {
    // App-lifetime scope on the computation dispatcher — the JVM analog of sage.android's
    // CoroutinesModule (which the JVM target can't use; it's sage.android). Currently only
    // RealDebugInfoManager injects a CoroutineScope; SupervisorJob so one failing debug-info
    // child flow can't cancel the whole scope.
    @Provides @SingleIn(AppScope::class)
    fun provideCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmEmulatorsModule {
    // Build the EmulatorProvider from direct singleton refs — matches the Android-side
    // EmulatorModule.kt in :cbox:android:player:emulators:di. No per-emulator @Provides
    // needed since nothing else in the graph injects an individual `*Emulator` type.
    @Provides @SingleIn(AppScope::class)
    fun provideEmulatorProvider(): EmulatorProvider = EmulatorProvider(
        listOf<Emulator>(
            GbaEmulator,
            GmeEmulator,
            NcsfEmulator,
            PsfEmulator,
            SsfEmulator,
            TwosfEmulator,
            UsfEmulator,
            VgmEmulator,
            // Last: broad catch-all, so dedicated chiptune emulators win shared extensions.
            VgmstreamEmulator,
        ),
    )
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmReadersModule {
    @Provides @SingleIn(AppScope::class) fun provideReaders(hatchet: Hatchet): Readers = Readers(hatchet)
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmScannerModule {
    @Provides @SingleIn(AppScope::class)
    fun provideRealScanner(
        repository: Repository,
        librarySource: LibrarySource,
        readers: Readers,
        hatchet: Hatchet,
    ): RealScanner = RealScanner(repository, librarySource, readers, VgmstreamProbe, hatchet)

    @Provides @SingleIn(AppScope::class)
    fun provideScanner(impl: RealScanner): Scanner = impl
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmGeneratorModule {
    @Provides @SingleIn(AppScope::class)
    fun provideRealGenerator(
        repository: Repository,
        contentSources: ContentSourceRegistry,
        bufferManager: ProducerBufferManager,
        emulatorProvider: EmulatorProvider,
        @Named("workDir") workDir: File,
        hatchet: Hatchet,
    ): RealGenerator = RealGenerator(
        repository,
        contentSources,
        bufferManager,
        emulatorProvider.emulators,
        File(workDir, "staging").absolutePath.toPath(),
        File(workDir, "pcm-cache").absolutePath.toPath(),
        FileSystem.SYSTEM,
        hatchet,
    )
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmSpeakerModule {
    /**
     * Real-time JVM speaker — backs `Speaker` (the abstract type Director receives) for the
     * desktop UI. The headless `FileSpeaker` (WAV output) was used by the removed `play` CLI
     * mode and is no longer wired into the graph.
     */
    @Provides @SingleIn(AppScope::class)
    fun provideLiveSpeaker(
        hatchet: Hatchet,
        bufferManager: ConsumerBufferManager,
        settingsManager: ChipboxSettingsManager,
        resamplers: Map<ResamplerMode, Resampler>,
    ): SourceDataLineSpeaker = SourceDataLineSpeaker(
        bufferManager,
        hatchet,
        resamplerFor(settingsManager, resamplers),
        deviceOutputSampleRate(hatchet),
    )

    /**
     * The single-technique resampler the speaker should use, resolved once from the saved setting:
     * OS mode → null (the line opens at the native rate and the OS mixer resamples), otherwise the
     * kernel for the mode. A one-time blocking read — no live switching, so a setting change applies
     * on the next launch.
     */
    private fun resamplerFor(
        settingsManager: ChipboxSettingsManager,
        resamplers: Map<ResamplerMode, Resampler>,
    ): Resampler? {
        val mode = runBlocking { settingsManager.getResamplerMode().first() }
        return if (mode == ResamplerMode.OS) null else resamplers[mode]
    }

    /**
     * The audio device's output rate for the in-app resampler modes (LINEAR/CUBIC), the desktop
     * analogue of Android's `AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE`: resampling to it keeps the
     * line on the device's own rate so the OS mixer never takes its arbitrary-ratio path.
     *
     * We ask `javax.sound` what the default mixer's output line supports. Many backends advertise
     * `NOT_SPECIFIED` ("any rate") rather than a concrete device rate — when that's all we get, we
     * fall back to a ubiquitous 48 kHz. OS mode ignores this and opens at the native rate.
     */
    private fun deviceOutputSampleRate(hatchet: Hatchet): Int {
        val advertised = runCatching {
            AudioSystem.getMixer(null).sourceLineInfo
                .filterIsInstance<DataLine.Info>()
                .flatMap { it.formats.toList() }
                .map { it.sampleRate }
                .filter { it > 0f } // drop AudioSystem.NOT_SPECIFIED (-1f)
                .map { it.toInt() }
                .toSortedSet()
        }.getOrElse {
            hatchet.w("Couldn't query device sample rates (${it.message}); using default.")
            sortedSetOf()
        }

        val chosen = when {
            advertised.isEmpty() -> DEFAULT_OUTPUT_SAMPLE_RATE
            DEFAULT_OUTPUT_SAMPLE_RATE in advertised -> DEFAULT_OUTPUT_SAMPLE_RATE
            else -> advertised.last() // highest concrete rate the device offers
        }
        hatchet.i("Desktop output rate: $chosen Hz (device advertised: ${advertised.toList()}).")
        return chosen
    }

    /** Fallback / preferred line rate when the backend reports no concrete device rate. */
    private const val DEFAULT_OUTPUT_SAMPLE_RATE = 48_000
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmStorageModule {
    @Provides @SingleIn(AppScope::class)
    fun provideStorage(@Named("workDir") workDir: File): Storage = JvmStorage(File(workDir, "settings.properties"))
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmCrashModule {
    // Crash reports go under the same per-OS app-data dir as the DB and settings (see Main.kt's
    // workDir). Shared by the writer (CrashReporter) and the reader (CrashReportStore).
    @Provides @SingleIn(AppScope::class) @Named("crashDir")
    fun provideCrashDir(@Named("workDir") workDir: File): File = File(workDir, "crashes")

    // Installed from main() after the graph is built.
    @Provides @SingleIn(AppScope::class)
    fun provideCrashReporter(
        @Named("crashDir") crashDir: File,
        appInfo: AppInfo,
        hatchet: Hatchet,
    ): CrashReporter = RealCrashReporter(
        crashDir = crashDir,
        appInfo = appInfo,
        hatchet = hatchet,
    )

    @Provides @SingleIn(AppScope::class)
    fun provideCrashReportStore(
        @Named("crashDir") crashDir: File,
        hatchet: Hatchet,
    ): CrashReportStore = RealCrashReportStore(crashDir = crashDir, hatchet = hatchet)
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmSettingsManagersModule {
    @Provides @SingleIn(AppScope::class)
    fun provideChipboxSettingsManager(storage: Storage): ChipboxSettingsManager =
        RealChipboxSettingsManager(storage)

    @Provides @SingleIn(AppScope::class)
    fun provideDebugSettingsManager(storage: Storage): DebugSettingsManager =
        RealDebugSettingsManager(storage)
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmAppInfoModule {
    // The Android target builds this from Gradle-injected BuildConfig fields; on JVM there's
    // no BuildConfig + no signed-build context, so the values are intentionally fake (debug
    // flag on, dev version) — good enough for the desktop bootstrap's Settings screen.
    @Provides @SingleIn(AppScope::class) fun provideAppInfo(): AppInfo = AppInfo(
        isDebug = true,
        versionName = "0.1.0-jvm",
        versionCode = 1,
        buildTimeMs = System.currentTimeMillis(),
        buildBranch = "desktop",
    )
}
