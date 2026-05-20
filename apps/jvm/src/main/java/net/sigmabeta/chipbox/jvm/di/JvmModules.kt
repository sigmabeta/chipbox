package net.sigmabeta.chipbox.jvm.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Module
import dagger.Provides
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.database.ChipboxDatabase
import net.sigmabeta.chipbox.debug.DebugSettingsManager
import net.sigmabeta.chipbox.debug.real.RealDebugSettingsManager
import net.sigmabeta.chipbox.jvm.JvmStorage
import net.sigmabeta.chipbox.jvm.LocalFileContentSource
import net.sigmabeta.chipbox.jvm.strings.JvmStringProvider
import net.sigmabeta.chipbox.jvm.strings.chipboxJvmStrings
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
import net.sigmabeta.chipbox.player.speaker.file.FileSpeaker
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
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

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

@Module
@ContributesTo(AppScope::class)
object HatchetModule {
    @Provides @Singleton fun provideHatchet(): Hatchet = BasicHatchet()
}

@Module
@ContributesTo(AppScope::class)
object JvmStringsModule {
    @Provides @Singleton
    fun provideStringProvider(): StringProvider = JvmStringProvider(chipboxJvmStrings)
}

@Module
@ContributesTo(AppScope::class)
object JvmDatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@Named("dbPath") path: String): ChipboxDatabase = Room
        .databaseBuilder<ChipboxDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

@Module
@ContributesTo(AppScope::class)
object JvmRepositoryModule {
    @Provides @Singleton
    fun provideDatabaseRepository(database: ChipboxDatabase, hatchet: Hatchet): DatabaseRepository =
        DatabaseRepository(database, hatchet)

    @Provides @Singleton
    fun provideRepository(impl: DatabaseRepository): Repository = impl
}

@Module
@ContributesTo(AppScope::class)
object JvmContentSourceModule {
    @Provides @Singleton fun provideLocalFileContentSource(): LocalFileContentSource = LocalFileContentSource()

    @Provides @Singleton
    fun provideLibrarySource(impl: LocalFileContentSource): LibrarySource = impl

    @Provides @Singleton
    fun provideContentSourceRegistry(impl: LocalFileContentSource): ContentSourceRegistry =
        ContentSourceRegistry(setOf<ContentSource>(impl))
}

@Module
@ContributesTo(AppScope::class)
object JvmBufferModule {
    @Provides @Singleton fun provideRealBufferManager(hatchet: Hatchet): RealBufferManager = RealBufferManager(hatchet)

    @Provides @Singleton
    fun provideProducer(impl: RealBufferManager): ProducerBufferManager = impl

    @Provides @Singleton
    fun provideConsumer(impl: RealBufferManager): ConsumerBufferManager = impl
}

@Module
@ContributesTo(AppScope::class)
object JvmEmulatorsModule {
    @Provides @Singleton fun provideGba(): GbaEmulator = GbaEmulator

    @Provides @Singleton fun provideGme(): GmeEmulator = GmeEmulator

    @Provides @Singleton fun providePsf(): PsfEmulator = PsfEmulator

    @Provides @Singleton fun provideSsf(): SsfEmulator = SsfEmulator

    @Provides @Singleton fun provideTwosf(): TwosfEmulator = TwosfEmulator

    @Provides @Singleton fun provideUsf(): UsfEmulator = UsfEmulator

    @Provides @Singleton fun provideVgm(): VgmEmulator = VgmEmulator

    @Provides @Singleton
    fun provideEmulatorProvider(
        gba: GbaEmulator,
        gme: GmeEmulator,
        psf: PsfEmulator,
        ssf: SsfEmulator,
        twosf: TwosfEmulator,
        usf: UsfEmulator,
        vgm: VgmEmulator,
    ): EmulatorProvider = EmulatorProvider(
        listOf<Emulator>(gba, gme, psf, ssf, twosf, usf, vgm)
    )
}

@Module
@ContributesTo(AppScope::class)
object JvmReadersModule {
    @Provides @Singleton fun provideReaders(hatchet: Hatchet): Readers = Readers(hatchet)
}

@Module
@ContributesTo(AppScope::class)
object JvmScannerModule {
    @Provides @Singleton
    fun provideRealScanner(
        repository: Repository,
        librarySource: LibrarySource,
        readers: Readers,
        hatchet: Hatchet,
    ): RealScanner = RealScanner(repository, librarySource, readers, hatchet)

    @Provides @Singleton
    fun provideScanner(impl: RealScanner): Scanner = impl
}

@Module
@ContributesTo(AppScope::class)
object JvmGeneratorModule {
    @Provides @Singleton
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

@Module
@ContributesTo(AppScope::class)
object JvmSpeakerModule {
    @Provides @Singleton
    fun provideFileSpeaker(
        @Named("outputDir") outputDir: File,
        hatchet: Hatchet,
        bufferManager: ConsumerBufferManager,
    ): FileSpeaker = FileSpeaker(outputDir, hatchet, bufferManager)
}

@Module
@ContributesTo(AppScope::class)
object JvmStorageModule {
    @Provides @Singleton fun provideStorage(): Storage = JvmStorage()
}

@Module
@ContributesTo(AppScope::class)
object JvmSettingsManagersModule {
    @Provides @Singleton
    fun provideChipboxSettingsManager(storage: Storage): ChipboxSettingsManager =
        RealChipboxSettingsManager(storage)

    @Provides @Singleton
    fun provideDebugSettingsManager(storage: Storage): DebugSettingsManager =
        RealDebugSettingsManager(storage)
}

@Module
@ContributesTo(AppScope::class)
object JvmAppInfoModule {
    // The Android target builds this from Gradle-injected BuildConfig fields; on JVM there's
    // no BuildConfig + no signed-build context, so the values are intentionally fake (debug
    // flag on, dev version) — good enough for the desktop bootstrap's Settings screen.
    @Provides @Singleton fun provideAppInfo(): AppInfo = AppInfo(
        isDebug = true,
        versionName = "0.1.0-jvm",
        versionCode = 1,
        buildTimeMs = System.currentTimeMillis(),
        buildBranch = "desktop",
    )
}
