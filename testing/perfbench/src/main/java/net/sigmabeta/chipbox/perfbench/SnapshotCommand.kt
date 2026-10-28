package net.sigmabeta.chipbox.perfbench

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import net.sigmabeta.chipbox.contentsource.LocalFileContentSource
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.chipbox.repository.FolderSnapshot
import net.sigmabeta.chipbox.repository.GameWriteOutcome
import net.sigmabeta.chipbox.repository.GameWriteResult
import net.sigmabeta.chipbox.repository.LibrarySnapshot
import net.sigmabeta.chipbox.repository.LibrarySnapshotJson
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.librarySnapshot
import net.sigmabeta.chipbox.scanner.real.RealScanner
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.sage.logging.BluntHatchet
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * `snapshot` — run the real host scanner once over a corpus and write a portable [LibrarySnapshot].
 *
 * The corpus is scanned exactly as the app would scan a library location (same readers, chain
 * resolution, m3u overlays), but the result is captured as the scanner's raw output rather than
 * persisted to a database, then stored with corpus-relative paths.
 */
fun runSnapshot(args: SnapshotArgs): LibrarySnapshot {
    val repository = SnapshotRepository()
    // The scanner reads its locations from a persisted file; a throwaway one keeps the run
    // self-contained and lets us point it at exactly the corpus under test.
    val locationsFile = File.createTempFile("perfbench-snapshot-locations", ".txt")
    val source = LocalFileContentSource(FileSystem.SYSTEM, locationsFile.toOkioPath())
    source.addLocation(args.corpusDir.toOkioPath())
    val readers = Readers(BluntHatchet())
    val scanner = RealScanner(repository, source, readers, BluntHatchet())

    val terminal = runBlocking {
        scanner.startScan()
        scanner.state().first { it is ScannerState.Complete || it is ScannerState.Failed }
    }
    locationsFile.delete()
    val complete = when (terminal) {
        is ScannerState.Complete -> terminal
        is ScannerState.Failed -> error("Snapshot scan failed at ${terminal.path}")
        else -> error("Snapshot scan ended in an unexpected state: $terminal")
    }

    val games = repository.games
    val snapshot = librarySnapshot(
        root = args.corpusDir.invariantSeparatorsPath,
        scannerVersion = scanner.version,
        readerVersions = readerVersions(readers, games),
        games = games,
    )
    args.outFile.parentFile?.mkdirs()
    args.outFile.writeText(LibrarySnapshotJson.encodeToString(snapshot))
    println(
        "Scanned ${games.size} game(s), ${games.sumOf { it.tracks.size }} track(s), " +
            "${complete.filesSkipped} skipped — wrote ${args.outFile.absolutePath}",
    )
    return snapshot
}

/** The reader version that handled each extension present in the scan, for the snapshot's metadata. */
private fun readerVersions(readers: Readers, games: List<RawGame>): Map<String, Int> =
    games.flatMap { it.tracks }
        .map { it.extension }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
        .associateWith { readers.readerVersionFor(it) }

/**
 * A write-only [Repository] that records the [RawGame]s a scan surfaces and stores nothing.
 *
 * A snapshot needs exactly the raw output [RealScanner] hands to
 * [Repository.upsertGame] — no catalog, no persistence — so this is the scanner's write seam
 * without a database. The scanner upserts from several folders at once, so the capture is guarded.
 * Reads are never reached by a scan and throw, surfacing a wrong assumption loudly rather than
 * returning an empty library.
 */
private class SnapshotRepository : Repository {
    private val captured = mutableListOf<RawGame>()
    private val lock = Any()

    val games: List<RawGame> get() = synchronized(lock) { captured.toList() }

    override suspend fun folderSnapshots(): Map<String, FolderSnapshot> = emptyMap()

    override suspend fun upsertGame(rawGame: RawGame): GameWriteOutcome {
        synchronized(lock) { captured += rawGame }
        // The game id is meaningless here and no store is kept; the scanner only reads the outcome
        // to raise a "game found" event.
        return GameWriteOutcome(0L, GameWriteResult.ADDED)
    }

    override suspend fun pruneGames(keptFolderKeys: Set<String>): List<String> = emptyList()

    override fun getAllArtists(withTracks: Boolean, withGames: Boolean, limit: Int?, offset: Int) = read()

    override fun getAllGames(withTracks: Boolean, withArtists: Boolean, limit: Int?, offset: Int) = read()

    override fun getAllTracks(withGame: Boolean, withArtists: Boolean, limit: Int?, offset: Int) = read()

    override fun getTracksByIds(ids: List<Long>, withGame: Boolean, withArtists: Boolean) = read()

    override fun getGamesByIds(ids: List<Long>, withTracks: Boolean, withArtists: Boolean) = read()

    override fun getArtistsByIds(ids: List<Long>, withTracks: Boolean, withGames: Boolean) = read()

    override suspend fun getTracksForGame(id: Long, withGame: Boolean, withArtists: Boolean) = read()

    override suspend fun getTracksForArtist(id: Long, withGame: Boolean, withArtists: Boolean) = read()

    override suspend fun getTracksForPlatform(platform: Platform, withGame: Boolean, withArtists: Boolean) = read()

    override fun getGamesForPlatform(platform: Platform) = read()

    override fun getRecentlyAddedGames(limit: Int, withinMs: Long) = read()

    override fun getUnplayedGames(playedGameIds: List<Long>, limit: Int) = read()

    override fun getAvailablePlatforms() = read()

    override fun getGame(id: Long, withTracks: Boolean, withArtists: Boolean) = read()

    override fun getArtist(id: Long, withTracks: Boolean, withGames: Boolean) = read()

    override suspend fun getTrack(id: Long, withGame: Boolean, withArtists: Boolean) = read()

    override suspend fun getRandomTrack() = read()

    override suspend fun getRandomGame() = read()

    override suspend fun getRandomArtist() = read()

    override suspend fun getGameCount() = read()

    override suspend fun getGameAtIndex(index: Int) = read()

    override suspend fun clearLibrary() = read()

    override fun searchGames(query: String) = read()

    override fun searchSongs(query: String) = read()

    override fun searchArtists(query: String) = read()

    override fun getSearchHistory() = read()

    override suspend fun addSearchHistory(query: String) = read()

    override suspend fun removeSearchHistory(id: Long) = read()

    private fun read(): Nothing =
        throw UnsupportedOperationException("snapshot generation only writes a library; reads are not implemented")
}
