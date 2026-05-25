package net.sigmabeta.chipbox.cli

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.sigmabeta.chipbox.database.ChipboxDatabase
import net.sigmabeta.chipbox.contentsource.LocalFileContentSource
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.emulators.vgmstream.VgmstreamProbe
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.database.DatabaseRepository
import net.sigmabeta.chipbox.scanner.real.RealScanner
import net.sigmabeta.chipbox.scanner.state.ScannerEvent
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.sage.logging.BluntHatchet
import net.sigmabeta.sage.logging.Hatchet
import java.io.File

/**
 * Headless assembly of the real Chipbox scan + library stack, wired by hand instead of through
 * Metro (the CLI needs only this slice, not the desktop app's full graph). Mirrors apps/jvm's
 * JvmModules: a bundled-SQLite Room database, a [DatabaseRepository] over it, a
 * [LocalFileContentSource] file walker, and the shared [RealScanner] driving them.
 *
 * The database lives under [workDir] and persists across runs, so re-scanning the same folder is
 * cheap (the scanner skips folders whose signature is unchanged).
 */
class ChipboxLibrary(
    workDir: File,
    hatchet: Hatchet = BluntHatchet(),
) {
    private val dbFile = File(workDir, DB_NAME)

    /** Where "Get cover art" reads IGDB credentials from (created as a template if absent). */
    val coverArtConfigFile = File(workDir, COVER_ART_CONFIG_NAME)

    /** Where "Get cover art" persists IGDB lookup results to skip re-querying on later runs. */
    val coverArtCacheFile = File(workDir, COVER_ART_CACHE_NAME)

    /** Where manual game→IGDB cover-art links are persisted, taking precedence over the search. */
    val coverArtOverridesFile = File(workDir, COVER_ART_OVERRIDES_NAME)

    private val database: ChipboxDatabase = Room
        .databaseBuilder<ChipboxDatabase>(name = dbFile.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        // The library is a derived cache; on a schema bump just rebuild it on the next scan.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    private val repository: Repository = DatabaseRepository(
        database.artistDao(),
        database.gameDao(),
        database.trackDao(),
        database.gameArtistDao(),
        database.trackArtistDao(),
        database.searchHistoryDao(),
        hatchet,
    )

    // Saved library locations persist across runs: LocalFileContentSource reads this file when
    // constructed and rewrites it whenever a location is added, so each run adds to the saved
    // library rather than replacing it. The scanner's prune step still drops games whose folders
    // are no longer present.
    private val contentSource = LocalFileContentSource(File(workDir, LOCATIONS_NAME))

    private val scanner = RealScanner(repository, contentSource, Readers(hatchet), VgmstreamProbe, hatchet)

    /** Adds [folder] to the saved library locations (persisted across runs; deduplicated). */
    fun addLibraryFolder(folder: File) = contentSource.addLocation(folder)

    /** Removes the saved library location with the given absolute [path]. */
    fun removeLibraryFolder(path: String) = contentSource.removeLibraryLocation(path)

    /** Absolute paths of every saved library location. */
    fun savedLocations(): List<String> = contentSource.locations.value.map { it.identifier }

    /** True once the library database file exists on disk (i.e. at least one scan has run). */
    fun hasDatabase(): Boolean = dbFile.exists()

    /**
     * Runs a full scan to completion, invoking [onGameFound] for each game discovered, and returns
     * the terminal [ScannerState] (Complete or Failed).
     */
    suspend fun scan(onGameFound: (name: String, trackCount: Int) -> Unit): ScannerState =
        coroutineScope {
            val events = launch { collectGameEvents(onGameFound) }
            scanner.startScan()
            val finalState = scanner.state().first(::isTerminalState)
            events.cancel()
            finalState
        }

    suspend fun games(): List<Game> = repository.getAllGames().firstSettled()

    suspend fun artists(): List<Artist> = repository.getAllArtists().firstSettled()

    suspend fun platforms(): List<Platform> = repository.getAvailablePlatforms().firstSettled()

    suspend fun tracks(): List<Track> = repository.getAllTracks().firstSettled()

    /** Every game with its tracks populated — used to plan a library reorganization. */
    suspend fun gamesWithTracks(): List<Game> =
        repository.getAllGames(withTracks = true).firstSettled()

    /** Song titles for [game], in track order. */
    suspend fun songTitlesForGame(game: Game): List<String> =
        repository.getTracksForGame(game.id).sortedBy { it.trackNumber }.map { it.title }

    /** Song titles credited to [artist]. */
    suspend fun songTitlesForArtist(artist: Artist): List<String> =
        repository.getTracksForArtist(artist.id).map { it.title }

    /** Games that have tracks on [platform]. */
    suspend fun gamesForPlatform(platform: Platform): List<Game> =
        repository.getGamesForPlatform(platform).firstSettled()

    /** Closes the underlying database. Call once when the CLI is done. */
    fun close() = database.close()

    private suspend fun collectGameEvents(onGameFound: (String, Int) -> Unit) {
        scanner.scanEvents().collect { event ->
            if (event is ScannerEvent.GameFoundEvent) onGameFound(event.name, event.trackCount)
        }
    }

    private suspend fun <T> Flow<Data<List<T>>>.firstSettled(): List<T> =
        when (val data = first { it !is Data.Loading }) {
            is Data.Succeeded -> data.data
            else -> emptyList()
        }

    private companion object {
        const val DB_NAME = "library.sqlite"
        const val LOCATIONS_NAME = "library-locations.txt"
        const val COVER_ART_CONFIG_NAME = "cover-art.cfg"
        const val COVER_ART_CACHE_NAME = "cover-art-cache.json"
        const val COVER_ART_OVERRIDES_NAME = "cover-art-overrides.json"

        fun isTerminalState(state: ScannerState): Boolean =
            state is ScannerState.Complete || state is ScannerState.Failed
    }
}
