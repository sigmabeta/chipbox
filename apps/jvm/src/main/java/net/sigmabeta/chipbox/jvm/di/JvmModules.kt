package net.sigmabeta.chipbox.jvm.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.database.ChipboxDatabase
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.real.RealDebugSettingsManager
import net.sigmabeta.chipbox.jvm.JvmStorage
import net.sigmabeta.chipbox.jvm.LocalFileContentSource
import net.sigmabeta.chipbox.jvm.SourceDataLineSpeaker
import net.sigmabeta.chipbox.jvm.strings.JvmStringProvider
import net.sigmabeta.chipbox.jvm.strings.chipboxJvmStrings
import net.sigmabeta.chipbox.player.buffer.BufferDebugSource
import net.sigmabeta.chipbox.player.buffer.ConsumerBufferManager
import net.sigmabeta.chipbox.player.buffer.ProducerBufferManager
import net.sigmabeta.chipbox.player.buffer.real.RealBufferManager
import net.sigmabeta.chipbox.player.emulators.Emulator
import net.sigmabeta.chipbox.player.emulators.EmulatorProvider
import net.sigmabeta.chipbox.player.emulators.gba.GbaEmulator
import net.sigmabeta.chipbox.player.emulators.gme.GmeEmulator
import net.sigmabeta.chipbox.player.emulators.psf.PsfEmulator
import net.sigmabeta.chipbox.player.emulators.ssf.SsfEmulator
import net.sigmabeta.chipbox.player.emulators.twosf.TwosfEmulator
import net.sigmabeta.chipbox.player.emulators.usf.UsfEmulator
import net.sigmabeta.chipbox.player.emulators.vgm.VgmEmulator
import net.sigmabeta.chipbox.player.generator.real.RealGenerator
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.database.DatabaseRepository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.real.RealScanner
import net.sigmabeta.chipbox.settings.ChipboxSettingsManager
import net.sigmabeta.chipbox.settings.real.RealChipboxSettingsManager
import net.sigmabeta.sage.appinfo.AppInfo
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.BasicHatchet
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.storage.common.Storage
import net.sigmabeta.sage.ui.StringProvider

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
    @Provides @SingleIn(AppScope::class) fun provideHatchet(): Hatchet = BasicHatchet()
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmStringsModule {
    @Provides @SingleIn(AppScope::class)
    fun provideStringProvider(): StringProvider = JvmStringProvider(chipboxJvmStrings)
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
        DatabaseRepository(database, hatchet)

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
            PsfEmulator,
            SsfEmulator,
            TwosfEmulator,
            UsfEmulator,
            VgmEmulator,
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
    ): RealScanner = RealScanner(repository, librarySource, readers, hatchet)

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
        File(workDir, "staging"),
        File(workDir, "pcm-cache"),
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
    ): SourceDataLineSpeaker = SourceDataLineSpeaker(bufferManager, hatchet)
}

@BindingContainer
@ContributesTo(AppScope::class)
object JvmStorageModule {
    @Provides @SingleIn(AppScope::class) fun provideStorage(): Storage = JvmStorage()
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
