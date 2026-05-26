package net.sigmabeta.chipbox.repository.database

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.models.ChainFile
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.GameWriteResult
import net.sigmabeta.chipbox.repository.RawGame
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.chipbox.repository.database.fakes.FakeDatabase
import net.sigmabeta.sage.logging.BluntHatchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the production [DatabaseRepository] against the in-memory DAO fakes in
 * [FakeDatabase]. Covers the upsert-and-reconcile contract the scanner relies on (ADDED /
 * UPDATED / UNCHANGED outcomes, per-track add/update/delete during rescan, artist parsing +
 * deduplication, pruneGames + orphan-artist cleanup), the Flow-based read APIs (Loading →
 * Succeeded sequence, Empty sentinel for the no-data case), and the search-history CRUD path.
 *
 * `UnconfinedTestDispatcher(testScheduler)` keeps everything single-threaded and synchronous —
 * with replay-1 StateFlow as the table-change trigger in [FakeDatabase], every write becomes
 * visible to subsequent Flow `.first()` collections before the test resumes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseRepositoryTest {

    // ---- upsertGame: outcome contract ----

    @Test
    fun `upsertGame on a brand-new folder reports ADDED with a fresh id`() = runTest {
        val (repo, _) = newRepo()
        val outcome = repo.upsertGame(rawGame("Chrono Trigger", "/library/chrono"))
        assertEquals(GameWriteResult.ADDED, outcome.result)
        assertTrue(outcome.gameId > 0, "a fresh primary key should be assigned")
    }

    @Test
    fun `re-scanning a byte-identical folder reports UNCHANGED`() = runTest {
        // The scanner is *supposed* to skip same-signature folders via folderSnapshots(); when
        // it doesn't (e.g. signature collision, defensive re-scan), the repo recognises the
        // identical-content case and reports UNCHANGED so the UI doesn't show a misleading
        // "X updated" count.
        val (repo, _) = newRepo()
        val game = rawGame("Chrono Trigger", "/library/chrono", tracks = listOf(rawTrack("Schala")))
        repo.upsertGame(game)
        val rescan = repo.upsertGame(game)
        assertEquals(GameWriteResult.UNCHANGED, rescan.result)
    }

    @Test
    fun `re-scanning preserves the same game id (folder-key reconciliation)`() = runTest {
        // Folder-key reconciliation: the same folder maps to the same game row across rescans
        // even if title metadata changes. Without this, every rescan would orphan + insert.
        val (repo, _) = newRepo()
        val first = repo.upsertGame(rawGame("Chrono Trigger", "/library/chrono"))
        val second = repo.upsertGame(rawGame("Chrono Trigger (Redux)", "/library/chrono"))
        assertEquals(first.gameId, second.gameId, "the game id should survive a title change")
        assertEquals(GameWriteResult.UPDATED, second.result)
    }

    @Test
    fun `metadata-only change (title or photo) reports UPDATED`() = runTest {
        val (repo, _) = newRepo()
        repo.upsertGame(rawGame("Old Title", "/library/x"))
        val outcome = repo.upsertGame(rawGame("New Title", "/library/x"))
        assertEquals(GameWriteResult.UPDATED, outcome.result)
    }

    @Test
    fun `adding a track on rescan reports UPDATED`() = runTest {
        val (repo, _) = newRepo()
        repo.upsertGame(rawGame("Game", "/library/x", tracks = listOf(rawTrack("Track 1"))))
        val outcome = repo.upsertGame(
            rawGame("Game", "/library/x", tracks = listOf(rawTrack("Track 1"), rawTrack("Track 2"))),
        )
        assertEquals(GameWriteResult.UPDATED, outcome.result)
    }

    @Test
    fun `removing a track on rescan reports UPDATED`() = runTest {
        val (repo, _) = newRepo()
        repo.upsertGame(
            rawGame("Game", "/library/x", tracks = listOf(rawTrack("Track 1"), rawTrack("Track 2"))),
        )
        val outcome = repo.upsertGame(rawGame("Game", "/library/x", tracks = listOf(rawTrack("Track 1"))))
        assertEquals(GameWriteResult.UPDATED, outcome.result)
    }

    @Test
    fun `modifying a track on rescan reports UPDATED`() = runTest {
        // Same path + trackNumber but a changed length / title should reconcile in place and
        // report UPDATED. Locks in that a length tweak doesn't slip through as UNCHANGED.
        val (repo, _) = newRepo()
        repo.upsertGame(rawGame("Game", "/library/x", tracks = listOf(rawTrack("Track 1", length = 60_000))))
        val outcome = repo.upsertGame(
            rawGame("Game", "/library/x", tracks = listOf(rawTrack("Track 1", length = 90_000))),
        )
        assertEquals(GameWriteResult.UPDATED, outcome.result)
    }

    @Test
    fun `rescan preserves track ids when reconciling by path-and-track-number`() = runTest {
        // Reconciling by (path, trackNumber) lets the queue / now-playing references survive a
        // rescan — without this, the user would lose their place every time the scanner runs.
        val (repo, db) = newRepo()
        repo.upsertGame(rawGame("Game", "/library/x", tracks = listOf(rawTrack("Schala"))))
        val originalId = db.tracks.values.single { it.title == "Schala" }.id

        // Re-scan with a title change on the same (path, trackNumber).
        repo.upsertGame(
            rawGame("Game", "/library/x", tracks = listOf(rawTrack("Schala", length = 90_000))),
        )

        val rescannedId = db.tracks.values.single { it.title == "Schala" }.id
        assertEquals(originalId, rescannedId, "(path, trackNumber) reconciliation must preserve track id")
    }

    // ---- artist handling ----

    @Test
    fun `artists are deduplicated across tracks within a single upsert`() = runTest {
        // Two tracks share an artist → one ArtistEntity, two TrackArtistJoins, and one
        // GameArtistJoin (artistWriteMutex's lookup-before-insert keeps it that way).
        val (repo, db) = newRepo()
        repo.upsertGame(
            rawGame(
                "Game",
                "/library/x",
                tracks = listOf(
                    rawTrack("One", artist = "Composer A"),
                    rawTrack("Two", artist = "Composer A"),
                ),
            ),
        )
        assertEquals(1, db.artists.size, "shared artist should land in one row")
        assertEquals(2, db.trackArtistJoins.count { it.artistId == db.artists.keys.first() })
        assertEquals(1, db.gameArtistJoins.size)
    }

    @Test
    fun `comma and ampersand split a track's artist field into multiple artists`() = runTest {
        // DELIMITERS_ARTISTS in the repo: ", &|,| or | and |&". Verify both common delimiters
        // produce separate artist rows that join back to the same track.
        val (repo, db) = newRepo()
        repo.upsertGame(
            rawGame(
                "Game",
                "/library/x",
                tracks = listOf(rawTrack("Co-write", artist = "Composer A & Composer B")),
            ),
        )
        assertEquals(setOf("Composer A", "Composer B"), db.artists.values.map { it.name }.toSet())
        val trackId = db.tracks.values.single().id
        assertEquals(2, db.trackArtistJoins.count { it.trackId == trackId })
    }

    @Test
    fun `the same artist across two games gets one row but two game-artist joins`() = runTest {
        val (repo, db) = newRepo()
        repo.upsertGame(rawGame("Game A", "/library/a", tracks = listOf(rawTrack("Theme A", artist = "Yuzo Koshiro"))))
        repo.upsertGame(rawGame("Game B", "/library/b", tracks = listOf(rawTrack("Theme B", artist = "Yuzo Koshiro"))))
        assertEquals(1, db.artists.size)
        assertEquals(2, db.gameArtistJoins.size)
    }

    @Test
    fun `rescan rebuilds the game's artist joins from scratch`() = runTest {
        // Adding an artist to a track via rescan should add a join; removing one should drop it.
        // Production rebuilds the join set in `linkArtists`; verify the delta lands.
        val (repo, db) = newRepo()
        repo.upsertGame(rawGame("Game", "/library/x", tracks = listOf(rawTrack("T", artist = "A"))))
        val gameId = db.games.values.single().id
        assertEquals(1, db.gameArtistJoins.count { it.gameId == gameId })

        repo.upsertGame(rawGame("Game", "/library/x", tracks = listOf(rawTrack("T", artist = "A & B"))))
        assertEquals(2, db.gameArtistJoins.count { it.gameId == gameId },
            "the new artist should appear in the game-artist join set")
    }

    // ---- prune ----

    @Test
    fun `pruneGames deletes games whose folder is not kept and returns their titles`() = runTest {
        val (repo, db) = newRepo()
        repo.upsertGame(rawGame("Keep", "/library/keep"))
        repo.upsertGame(rawGame("Drop", "/library/drop"))

        val removed = repo.pruneGames(keptFolderKeys = setOf("/library/keep"))

        assertEquals(listOf("Drop"), removed)
        assertEquals(setOf("Keep"), db.games.values.map { it.title }.toSet())
    }

    @Test
    fun `pruneGames cascades to tracks and drops orphaned artists`() = runTest {
        // When the only game referencing an artist is pruned, deleteOrphans should sweep that
        // artist row away too. Tracks should cascade with the game (the fake mimics the FK
        // cascade so we can assert it).
        val (repo, db) = newRepo()
        repo.upsertGame(
            rawGame("Lone Game", "/library/lone", tracks = listOf(rawTrack("T", artist = "Solo Artist"))),
        )
        assertEquals(1, db.artists.size)
        assertEquals(1, db.tracks.size)

        repo.pruneGames(keptFolderKeys = emptySet())

        assertTrue(db.games.isEmpty(), "all games should be gone")
        assertTrue(db.tracks.isEmpty(), "tracks should cascade with their game")
        assertTrue(db.artists.isEmpty(), "orphaned artist should be swept by deleteOrphans")
    }

    @Test
    fun `pruneGames keeps an artist still referenced by another game`() = runTest {
        val (repo, db) = newRepo()
        repo.upsertGame(rawGame("Drop", "/library/drop", tracks = listOf(rawTrack("T", artist = "Shared"))))
        repo.upsertGame(rawGame("Keep", "/library/keep", tracks = listOf(rawTrack("U", artist = "Shared"))))

        repo.pruneGames(keptFolderKeys = setOf("/library/keep"))

        assertEquals(1, db.artists.size, "shared artist must survive when another track still references them")
    }

    // ---- Flow read APIs ----

    @Test
    fun `getAllGames emits Loading then Succeeded after upserts`() = runTest {
        val (repo, _) = newRepo()
        repo.upsertGame(rawGame("Game", "/library/x"))
        val data = repo.getAllGames().first { it !is Data.Loading }
        assertTrue(data is Data.Succeeded, "expected Succeeded; got $data")
        assertEquals(listOf("Game"), data.data.map { it.title })
    }

    @Test
    fun `getAllGames reports Empty before any upsert`() = runTest {
        val (repo, _) = newRepo()
        assertEquals(Data.Empty, repo.getAllGames().first { it !is Data.Loading })
    }

    @Test
    fun `getGame by id returns Succeeded for an existing row`() = runTest {
        val (repo, _) = newRepo()
        val id = repo.upsertGame(rawGame("Game", "/library/x")).gameId
        val data = repo.getGame(id, withTracks = true).first { it !is Data.Loading }
        assertTrue(data is Data.Succeeded)
        assertEquals("Game", data.data?.title)
    }

    @Test
    fun `getGame by id returns Empty when the id is not present`() = runTest {
        val (repo, _) = newRepo()
        assertEquals(Data.Empty, repo.getGame(id = 99_999L).first { it !is Data.Loading })
    }

    @Test
    fun `getTracksForGame returns only that game's tracks`() = runTest {
        val (repo, _) = newRepo()
        val gameId = repo.upsertGame(
            rawGame("Game A", "/library/a", tracks = listOf(rawTrack("A1"), rawTrack("A2"))),
        ).gameId
        repo.upsertGame(rawGame("Game B", "/library/b", tracks = listOf(rawTrack("B1"))))

        val tracks = repo.getTracksForGame(gameId)
        assertEquals(setOf("A1", "A2"), tracks.map { it.title }.toSet())
    }

    @Test
    fun `getTracksForPlatform filters by Platform name`() = runTest {
        val (repo, _) = newRepo()
        repo.upsertGame(rawGame("SNES Game", "/library/s", tracks = listOf(rawTrack("S", platform = Platform.SNES))))
        repo.upsertGame(rawGame("NES Game", "/library/n", tracks = listOf(rawTrack("N", platform = Platform.NES))))

        val snesTracks = repo.getTracksForPlatform(Platform.SNES)
        assertEquals(listOf("S"), snesTracks.map { it.title })
    }

    @Test
    fun `getGamesForPlatform returns games that have any track on the platform`() = runTest {
        val (repo, _) = newRepo()
        repo.upsertGame(rawGame("Game", "/library/x", tracks = listOf(rawTrack("T", platform = Platform.PSX))))
        val data = repo.getGamesForPlatform(Platform.PSX).first { it !is Data.Loading }
        assertTrue(data is Data.Succeeded)
        assertEquals(listOf("Game"), data.data.map { it.title })
    }

    @Test
    fun `getAvailablePlatforms returns the distinct set of track platforms`() = runTest {
        val (repo, _) = newRepo()
        repo.upsertGame(
            rawGame(
                "Mixed",
                "/library/m",
                tracks = listOf(
                    rawTrack("S", platform = Platform.SNES),
                    rawTrack("S2", platform = Platform.SNES),
                    rawTrack("N", platform = Platform.NES),
                ),
            ),
        )
        val data = repo.getAvailablePlatforms().first { it !is Data.Loading }
        assertTrue(data is Data.Succeeded)
        assertEquals(setOf(Platform.SNES, Platform.NES), data.data.toSet())
    }

    // ---- search ----

    @Test
    fun `searchGames does a case-insensitive substring match`() = runTest {
        val (repo, _) = newRepo()
        repo.upsertGame(rawGame("Chrono Trigger", "/library/ct"))
        repo.upsertGame(rawGame("Super Mario World", "/library/smw"))

        val data = repo.searchGames("CHRONO").first { it !is Data.Loading }
        assertTrue(data is Data.Succeeded)
        assertEquals(listOf("Chrono Trigger"), data.data.map { it.title })
    }

    @Test
    fun `searchSongs and searchArtists also do case-insensitive substring matching`() = runTest {
        val (repo, _) = newRepo()
        repo.upsertGame(rawGame("Game", "/library/x", tracks = listOf(rawTrack("Schala's Theme", artist = "Yasunori Mitsuda"))))

        val songs = repo.searchSongs("schala").first { it !is Data.Loading }
        assertTrue(songs is Data.Succeeded)
        assertEquals(listOf("Schala's Theme"), songs.data.map { it.title })

        val artists = repo.searchArtists("mitsuda").first { it !is Data.Loading }
        assertTrue(artists is Data.Succeeded)
        assertEquals(listOf("Yasunori Mitsuda"), artists.data.map { it.name })
    }

    @Test
    fun `search returns Empty on no matches`() = runTest {
        val (repo, _) = newRepo()
        repo.upsertGame(rawGame("Game", "/library/x"))
        assertEquals(Data.Empty, repo.searchGames("nothing-matches").first { it !is Data.Loading })
    }

    // ---- search history ----

    @Test
    fun `addSearchHistory dedupes the same query and is otherwise additive`() = runTest {
        val (repo, _) = newRepo()
        repo.addSearchHistory("query one")
        repo.addSearchHistory("query one") // duplicate — should be a no-op
        repo.addSearchHistory("query two")

        val data = repo.getSearchHistory().first { it !is Data.Loading }
        assertTrue(data is Data.Succeeded)
        assertEquals(setOf("query one", "query two"), data.data.map { it.query }.toSet())
    }

    @Test
    fun `removeSearchHistory drops only the entry with the matching id`() = runTest {
        val (repo, db) = newRepo()
        repo.addSearchHistory("alpha")
        repo.addSearchHistory("beta")
        val betaId = db.searchHistory.values.single { it.query == "beta" }.id

        repo.removeSearchHistory(betaId)

        val data = repo.getSearchHistory().first { it !is Data.Loading }
        assertTrue(data is Data.Succeeded)
        assertEquals(listOf("alpha"), data.data.map { it.query })
    }

    // ---- single-row reads ----

    @Test
    fun `getTrack returns the track by id with optional joins`() = runTest {
        val (repo, db) = newRepo()
        repo.upsertGame(
            rawGame("Game", "/library/x", tracks = listOf(rawTrack("Schala", artist = "Yasunori Mitsuda"))),
        )
        val trackId = db.tracks.values.single().id

        val bare = repo.getTrack(trackId)
        assertNotNull(bare)
        assertEquals("Schala", bare.title)
        assertNull(bare.artists, "withArtists=false should leave the join unloaded")

        val withJoins = repo.getTrack(trackId, withGame = true, withArtists = true)
        assertNotNull(withJoins)
        assertEquals("Game", withJoins.game?.title)
        assertEquals(listOf("Yasunori Mitsuda"), withJoins.artists?.map { it.name })
    }

    @Test
    fun `getTrack returns null for an unknown id`() = runTest {
        val (repo, _) = newRepo()
        assertNull(repo.getTrack(id = 99_999L))
    }

    // ---- snapshots + teardown ----

    @Test
    fun `folderSnapshots reports each game's signature plus track count`() = runTest {
        val (repo, _) = newRepo()
        repo.upsertGame(
            rawGame("Game A", "/library/a", signature = "sig-A", tracks = listOf(rawTrack("A1"), rawTrack("A2"))),
        )
        repo.upsertGame(rawGame("Game B", "/library/b", signature = "sig-B"))

        val snapshots = repo.folderSnapshots()
        assertEquals(2, snapshots.getValue("/library/a").trackCount)
        assertEquals("sig-A", snapshots.getValue("/library/a").signature)
        assertEquals(0, snapshots.getValue("/library/b").trackCount)
    }

    @Test
    fun `clearLibrary empties the library tables but leaves search history alone`() = runTest {
        // clearLibrary() is wired to the dedicated "Clear Library" button in
        // SettingsViewModel.onClearLibraryClicked. It nukes the 5 library tables (games,
        // tracks, artists, both join tables) but intentionally does not touch search history:
        // the user's typed search history is independent of which games happen to be in the
        // library, so wiping the library shouldn't drop their recent queries.
        val (repo, db) = newRepo()
        repo.upsertGame(
            rawGame("Game", "/library/x", tracks = listOf(rawTrack("T", artist = "A"))),
        )
        repo.addSearchHistory("query")

        repo.clearLibrary()

        assertTrue(db.games.isEmpty(), "games table should be cleared")
        assertTrue(db.tracks.isEmpty(), "tracks table should be cleared")
        assertTrue(db.artists.isEmpty(), "artists table should be cleared")
        assertTrue(db.gameArtistJoins.isEmpty(), "game-artist joins should be cleared")
        assertTrue(db.trackArtistJoins.isEmpty(), "track-artist joins should be cleared")
        assertEquals(1, db.searchHistory.size, "search history is intentionally preserved across clearLibrary")
    }

    @Test
    fun `chain files round-trip through encode-decode without changing the track entity`() = runTest {
        // The chainFiles column is the encoded form; decodeChainFiles is what hands the parsed
        // list back to the Track model. Verify the round trip survives both directions.
        val (repo, db) = newRepo()
        val chain = listOf(ChainFile("game.psflib", "/library/game.psflib"))
        repo.upsertGame(
            rawGame("Game", "/library/x", tracks = listOf(rawTrack("T", chainFiles = chain))),
        )
        val trackId = db.tracks.values.single().id

        val decoded = repo.getTrack(trackId)
        assertNotNull(decoded)
        assertEquals(chain, decoded.chainFiles)
    }

    // ---- helpers ----

    private fun newRepo(): Pair<DatabaseRepository, FakeDatabase> {
        val db = FakeDatabase()
        val repo = DatabaseRepository(
            artistDao = db.artistDao,
            gameDao = db.gameDao,
            trackDao = db.trackDao,
            gameArtistDao = db.gameArtistDao,
            trackArtistDao = db.trackArtistDao,
            searchHistoryDao = db.searchHistoryDao,
            hatchet = BluntHatchet(),
            dispatcher = UnconfinedTestDispatcher(),
        )
        return repo to db
    }

    private fun rawGame(
        title: String,
        folderKey: String,
        signature: String = "sig-${title.hashCode()}",
        tracks: List<RawTrack> = emptyList(),
        photoUrl: String? = null,
    ): RawGame = RawGame(
        title = title,
        photoUrl = photoUrl,
        folderKey = folderKey,
        folderSignature = signature,
        tracks = tracks,
    )

    private fun rawTrack(
        title: String,
        artist: String = "Unknown",
        path: String = "/library/${title}.psf",
        trackNumber: Int = 0,
        length: Long = 60_000L,
        platform: Platform = Platform.OTHER,
        chainFiles: List<ChainFile> = emptyList(),
    ): RawTrack = RawTrack(
        path = path,
        source = "test",
        title = title,
        artist = artist,
        game = "Unused",
        length = length,
        trackNumber = trackNumber,
        fadeLengthMs = 0L,
        chainFiles = chainFiles,
        extension = "psf",
        platform = platform,
    )
}
