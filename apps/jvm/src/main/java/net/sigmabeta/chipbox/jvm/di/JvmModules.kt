package net.sigmabeta.chipbox.jvm.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.Dispatchers
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.database.ChipboxDatabase
import net.sigmabeta.chipbox.jvm.LocalFileContentSource
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
import net.sigmabeta.chipbox.scanner.real.RealScanner
import net.sigmabeta.sage.logging.BasicHatchet
import net.sigmabeta.sage.logging.Hatchet
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
object HatchetModule {
    @Provides @Singleton fun provideHatchet(): Hatchet = BasicHatchet()
}

@Module
object JvmDatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@Named("dbPath") path: String): ChipboxDatabase = Room
        .databaseBuilder<ChipboxDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

@Module
object JvmRepositoryModule {
    @Provides @Singleton
    fun provideDatabaseRepository(database: ChipboxDatabase, hatchet: Hatchet): DatabaseRepository =
        DatabaseRepository(database, hatchet)

    @Provides @Singleton
    fun provideRepository(impl: DatabaseRepository): Repository = impl
}

@Module
object JvmContentSourceModule {
    @Provides @Singleton fun provideLocalFileContentSource() = LocalFileContentSource()

    @Provides @Singleton
    fun provideLibrarySource(impl: LocalFileContentSource): LibrarySource = impl

    @Provides @Singleton
    fun provideContentSourceRegistry(impl: LocalFileContentSource): ContentSourceRegistry =
        ContentSourceRegistry(setOf<ContentSource>(impl))
}

@Module
object JvmBufferModule {
    @Provides @Singleton fun provideRealBufferManager(hatchet: Hatchet) = RealBufferManager(hatchet)

    @Provides @Singleton
    fun provideProducer(impl: RealBufferManager): ProducerBufferManager = impl

    @Provides @Singleton
    fun provideConsumer(impl: RealBufferManager): ConsumerBufferManager = impl
}

@Module
object JvmEmulatorsModule {
    @Provides @Singleton fun provideGba() = GbaEmulator
    @Provides @Singleton fun provideGme() = GmeEmulator
    @Provides @Singleton fun providePsf() = PsfEmulator
    @Provides @Singleton fun provideSsf() = SsfEmulator
    @Provides @Singleton fun provideTwosf() = TwosfEmulator
    @Provides @Singleton fun provideUsf() = UsfEmulator
    @Provides @Singleton fun provideVgm() = VgmEmulator

    @Provides @Singleton
    fun provideEmulatorProvider(
        gba: GbaEmulator, gme: GmeEmulator, psf: PsfEmulator, ssf: SsfEmulator,
        twosf: TwosfEmulator, usf: UsfEmulator, vgm: VgmEmulator,
    ): EmulatorProvider = EmulatorProvider(
        listOf<Emulator>(gba, gme, psf, ssf, twosf, usf, vgm)
    )
}

@Module
object JvmReadersModule {
    @Provides @Singleton fun provideReaders(hatchet: Hatchet): Readers = Readers(hatchet)
}

@Module
object JvmScannerModule {
    @Provides @Singleton
    fun provideRealScanner(
        repository: Repository,
        librarySource: LibrarySource,
        readers: Readers,
        hatchet: Hatchet,
    ): RealScanner = RealScanner(repository, librarySource, readers, hatchet)
}

@Module
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
object JvmSpeakerModule {
    @Provides @Singleton
    fun provideFileSpeaker(
        @Named("outputDir") outputDir: File,
        hatchet: Hatchet,
        bufferManager: ConsumerBufferManager,
    ): FileSpeaker = FileSpeaker(outputDir, hatchet, bufferManager)
}
