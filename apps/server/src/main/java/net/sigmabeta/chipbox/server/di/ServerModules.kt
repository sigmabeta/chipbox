package net.sigmabeta.chipbox.server.di

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
import kotlinx.coroutines.runBlocking
import net.sigmabeta.chipbox.contentsource.ContentSource
import net.sigmabeta.chipbox.contentsource.ContentSourceRegistry
import net.sigmabeta.chipbox.contentsource.LibrarySource
import net.sigmabeta.chipbox.contentsource.LocalFileContentSource
import net.sigmabeta.chipbox.database.ChipboxDatabase
import net.sigmabeta.chipbox.player.emulators.vgmstream.VgmstreamProbe
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.database.DatabaseRepository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.real.RealScanner
import net.sigmabeta.chipbox.server.logging.ServerHatchet
import net.sigmabeta.chipbox.strings.real.ChipboxStringProvider
import net.sigmabeta.chipbox.strings.real.loadChipboxStrings
import net.sigmabeta.sage.di.AppScope
import net.sigmabeta.sage.logging.Hatchet
import net.sigmabeta.sage.ui.StringProvider

/**
 * Binding containers for the headless HTTP server. Modelled on `JvmModules.kt` in `apps/jvm` —
 * almost a copy/paste, but stripped of every player/buffer/emulator/generator/speaker/UI module
 * (the server doesn't decode audio; the browser will). Differences from the desktop graph:
 *
 *  - **No HatchetModule from JvmHatchet** — uses [ServerHatchet] (routes through stdout/stderr;
 *    no per-frame stack-walk that the desktop JvmHatchet does, since the server log volume is
 *    much lower than the desktop UI's).
 *  - **No JvmStorageModule / SettingsModule / AppInfoModule** — the server has no settings UI;
 *    no user-facing About screen.
 *  - **No JvmBufferModule / JvmEmulatorsModule / JvmGeneratorModule / JvmSpeakerModule** —
 *    playback isn't a server concern.
 *  - **No JvmFileSystemModule** — the FolderPicker UI is the only consumer of FileSystem.SYSTEM
 *    on JVM today; the server doesn't render UI.
 */

@BindingContainer
@ContributesTo(AppScope::class)
object ServerHatchetModule {
    @Provides @SingleIn(AppScope::class) fun provideHatchet(): Hatchet = ServerHatchet()
}

@BindingContainer
@ContributesTo(AppScope::class)
object ServerStringsModule {
    // Single multiplatform source: cbox/common/strings/real's composeResources, preloaded once.
    // The scanner pulls localized platform names through StringProvider when bucketing tracks.
    @Provides @SingleIn(AppScope::class)
    fun provideStringProvider(): StringProvider = runBlocking { ChipboxStringProvider(loadChipboxStrings()) }
}

@BindingContainer
@ContributesTo(AppScope::class)
object ServerDatabaseModule {
    @Provides @SingleIn(AppScope::class)
    fun provideDatabase(@Named("dbPath") path: String): ChipboxDatabase = Room
        .databaseBuilder<ChipboxDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        // Library is a derived cache; on a schema bump just rebuild it on the next scan.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}

@BindingContainer
@ContributesTo(AppScope::class)
object ServerRepositoryModule {
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
object ServerContentSourceModule {
    @Provides @SingleIn(AppScope::class)
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
object ServerCoroutinesModule {
    // App-lifetime scope for background work (the startup scan). SupervisorJob keeps one failing
    // child from cancelling siblings — same shape as JvmCoroutinesModule.
    @Provides @SingleIn(AppScope::class)
    fun provideCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@BindingContainer
@ContributesTo(AppScope::class)
object ServerReadersModule {
    @Provides @SingleIn(AppScope::class) fun provideReaders(hatchet: Hatchet): Readers = Readers(hatchet)
}

@BindingContainer
@ContributesTo(AppScope::class)
object ServerScannerModule {
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
