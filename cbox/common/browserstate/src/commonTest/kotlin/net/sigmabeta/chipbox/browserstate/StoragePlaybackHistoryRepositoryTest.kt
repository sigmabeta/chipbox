package net.sigmabeta.chipbox.browserstate

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.history.PlayCount
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Play history over the shared versioned browser store. The log is one [PlayHistoryState] payload
 * at `chipbox.playback_history`; the Home recently-/most-played surfaces are folds over it, and the
 * old unversioned JSON array is accepted as version 0.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoragePlaybackHistoryRepositoryTest {

    @Test
    fun `plays survive repository recreation and feed the home surfaces`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val clock = FakeClock()
        val repo = StoragePlaybackHistoryRepository(storage, events, clock::now)

        repo.recordPlay(trackOf(10L, gameId = 100L, artistIds = listOf(1L, 2L))) // t=1
        repo.recordPlay(trackOf(10L, gameId = 100L, artistIds = listOf(1L, 2L))) // t=2
        repo.recordPlay(trackOf(11L, gameId = 100L)) // t=3
        repo.recordPlay(trackOf(12L, gameId = 200L)) // t=4

        val reloaded = StoragePlaybackHistoryRepository(storage, events, clock::now)

        assertEquals(
            listOf(12L, 11L, 10L),
            reloaded.recentlyPlayed(limit = 10).first().map { it.trackId },
            "recently played is distinct by track, newest first",
        )
        assertEquals(
            listOf(PlayCount(id = 10L, playCount = 2, lastPlayedMs = 2L)),
            reloaded.mostPlayedSongs(limit = 10).first(),
            "only tracks played more than once are 'most played'",
        )
        assertEquals(
            listOf(PlayCount(id = 100L, playCount = 3, lastPlayedMs = 3L)),
            reloaded.mostPlayedGames(limit = 10).first(),
        )
        assertEquals(
            listOf(1L, 2L),
            reloaded.mostPlayedArtists(limit = 10).first().map { it.id },
            "a multi-artist track credits every artist",
        )
        assertEquals(listOf(100L, 200L), reloaded.playedGameIds().first())
    }

    @Test
    fun `history is bounded to 500 plays and trims the oldest`() = runTest {
        // Seed a full log instead of recording 501 plays: every record rewrites the whole blob, so
        // growing it one play at a time is O(n²) of JSON — fast on a dev machine, but past the ~2s
        // per-test limit of the Node runner on a loaded CI box. One record past a seeded full log
        // exercises the same trim-on-write path.
        val seeded = PlayHistoryState(
            plays = (0L until PlayHistoryState.MAX_ENTRIES.toLong()).map { index ->
                PlayHistoryEntry(trackId = index, gameId = 0L, timeMs = index + 1)
            },
        )
        val storage = FakeStorage(
            mapOf(BrowserStateKeys.PLAYBACK_HISTORY to BrowserStateCodecs.playHistory.encode(seeded)),
        )
        val events = BrowserStateEvents()
        val clock = FakeClock(next = 1_000L)
        val repo = StoragePlaybackHistoryRepository(storage, events, clock::now)

        repo.recordPlay(trackOf(PlayHistoryState.MAX_ENTRIES.toLong()))

        val recent = StoragePlaybackHistoryRepository(storage, events, clock::now)
            .recentlyPlayed(limit = PlayHistoryState.MAX_ENTRIES + 10)
            .first()

        assertEquals(PlayHistoryState.MAX_ENTRIES, recent.size)
        assertFalse(recent.any { it.trackId == 0L }, "the oldest play was trimmed")
        assertEquals(500L, recent.first().trackId, "the newest play is kept")
        assertEquals(1L, recent.last().trackId, "the 500 newest plays remain")
    }

    @Test
    fun `trimming keeps the newest plays by timestamp even when the log is out of order`() {
        val outOfOrder = PlayHistoryState(
            plays = listOf(
                PlayHistoryEntry(trackId = 1L, gameId = 0L, timeMs = 30L),
                PlayHistoryEntry(trackId = 2L, gameId = 0L, timeMs = 10L),
                PlayHistoryEntry(trackId = 3L, gameId = 0L, timeMs = 20L),
            ),
        )

        val bounded = outOfOrder.bounded(maxEntries = 2)

        assertEquals(listOf(3L, 1L), bounded.plays.map { it.trackId }, "oldest timestamp is dropped, order stays chronological")
    }

    @Test
    fun `a legacy raw JSON array is read as version zero and reported as migrated`() = runTest {
        val legacy = """
            [{"trackId":7,"gameId":3,"artistIds":[1],"timeMs":100},
             {"trackId":8,"gameId":3,"artistIds":[],"timeMs":200}]
        """.trimIndent()
        val storage = FakeStorage(mapOf(BrowserStateKeys.PLAYBACK_HISTORY to legacy))
        val events = BrowserStateEvents()
        val seen = collectEvents(events)

        val repo = StoragePlaybackHistoryRepository(storage, events, FakeClock()::now)

        assertEquals(listOf(8L, 7L), repo.recentlyPlayed(10).first().map { it.trackId })
        assertTrue(seen.any { it.reason == BrowserStateReason.MIGRATED }, "$seen")
    }

    @Test
    fun `an oversized legacy array is capped on read`() = runTest {
        val legacy = (0..PlayHistoryState.MAX_ENTRIES).joinToString(prefix = "[", postfix = "]") { index ->
            """{"trackId":$index,"gameId":0,"artistIds":[],"timeMs":$index}"""
        }
        val storage = FakeStorage(mapOf(BrowserStateKeys.PLAYBACK_HISTORY to legacy))
        val repo = StoragePlaybackHistoryRepository(storage, BrowserStateEvents(), FakeClock()::now)

        val recent = repo.recentlyPlayed(limit = PlayHistoryState.MAX_ENTRIES + 10).first()

        assertEquals(PlayHistoryState.MAX_ENTRIES, recent.size)
        assertFalse(recent.any { it.trackId == 0L }, "the oldest legacy play is not surfaced")
    }

    @Test
    fun `corrupt history falls back to empty and surfaces a non-fatal message`() = runTest {
        val storage = FakeStorage(mapOf(BrowserStateKeys.PLAYBACK_HISTORY to "{not json"))
        val events = BrowserStateEvents()
        val seen = collectEvents(events)
        val repo = StoragePlaybackHistoryRepository(storage, events, FakeClock()::now)

        assertEquals(emptyList(), repo.recentlyPlayed(10).first())
        assertTrue(seen.any { it.reason == BrowserStateReason.CORRUPT })
    }

    @Test
    fun `a newer history schema falls back safely`() = runTest {
        val storage = FakeStorage(
            mapOf(BrowserStateKeys.PLAYBACK_HISTORY to """{"schemaVersion":99,"payload":{"plays":[]}}"""),
        )
        val events = BrowserStateEvents()
        val seen = collectEvents(events)

        val repo = StoragePlaybackHistoryRepository(storage, events, FakeClock()::now)

        assertEquals(emptyList(), repo.recentlyPlayed(10).first())
        assertTrue(seen.any { it.reason == BrowserStateReason.UNSUPPORTED_VERSION })
    }

    @Test
    fun `a quota failure on record is caught and surfaced without throwing`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val seen = collectEvents(events)
        val repo = StoragePlaybackHistoryRepository(storage, events, FakeClock()::now)
        storage.failWrites = true

        repo.recordPlay(trackOf(5L))

        assertTrue(seen.any { it.reason == BrowserStateReason.WRITE_FAILED }, "$seen")
        assertEquals(emptyList(), repo.recentlyPlayed(10).first())
    }

    @Test
    fun `another tab's history write reaches a live reader`() = runTest {
        val storage = FakeStorage()
        val events = BrowserStateEvents()
        val repo = StoragePlaybackHistoryRepository(storage, events, FakeClock()::now)
        val seen = mutableListOf<List<Long>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.recentlyPlayed(10).collect { plays -> seen += plays.map { it.trackId } }
        }

        val external = BrowserStateCodecs.playHistory.encode(
            PlayHistoryState(plays = listOf(PlayHistoryEntry(trackId = 77L, gameId = 1L, timeMs = 5L))),
        )
        storage.writeFromOtherTab(BrowserStateKeys.PLAYBACK_HISTORY, external)

        assertEquals(listOf(emptyList(), listOf(77L)), seen)
    }

    @Test
    fun `unknown ids stay in the log for the caller to resolve`() = runTest {
        val repo = StoragePlaybackHistoryRepository(FakeStorage(), BrowserStateEvents(), FakeClock()::now)
        repo.recordPlay(trackOf(404L))
        repo.recordPlay(trackOf(7L))

        assertEquals(
            listOf(7L, 404L),
            repo.recentlyPlayed(10).first().map { it.trackId },
            "the raw log retains both ids; library hydration drops unknown ones",
        )
    }

    @Test
    fun `clearHistory empties the log`() = runTest {
        val repo = StoragePlaybackHistoryRepository(FakeStorage(), BrowserStateEvents(), FakeClock()::now)
        repo.recordPlay(trackOf(1L))
        repo.recordPlay(trackOf(1L))

        repo.clearHistory()

        assertEquals(emptyList(), repo.recentlyPlayed(10).first())
        assertEquals(emptyList(), repo.mostPlayedSongs(10).first())
    }

    @Test
    fun `clearing corrupt history rewrites the key so a reload is empty and usable`() = runTest {
        val storage = FakeStorage(mapOf(BrowserStateKeys.PLAYBACK_HISTORY to "{not json"))
        val events = BrowserStateEvents()
        val clock = FakeClock()
        val repo = StoragePlaybackHistoryRepository(storage, events, clock::now)

        repo.clearHistory()

        assertTrue(
            BrowserStateCodecs.playHistory.decode(storage.raw(BrowserStateKeys.PLAYBACK_HISTORY)) is DecodeOutcome.Success,
            "the corrupt string is replaced, not left in storage: ${storage.raw(BrowserStateKeys.PLAYBACK_HISTORY)}",
        )
        val reloaded = StoragePlaybackHistoryRepository(storage, events, clock::now)
        assertEquals(emptyList(), reloaded.recentlyPlayed(10).first(), "a reload stays empty")
        reloaded.recordPlay(trackOf(9L))
        assertEquals(listOf(9L), reloaded.recentlyPlayed(10).first().map { it.trackId }, "the store is usable again")
    }

    private fun TestScope.collectEvents(events: BrowserStateEvents): MutableList<BrowserStateEvent> {
        val seen = mutableListOf<BrowserStateEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            events.events.collect { seen += it }
        }
        return seen
    }

    private fun trackOf(id: Long, gameId: Long = 0L, artistIds: List<Long> = emptyList()): Track = Track(
        id = id,
        path = "/library/track$id.psf",
        source = "test",
        title = "Track $id",
        trackLengthMs = 60_000L,
        trackNumber = 0,
        fadeLengthMs = 0L,
        game = null,
        artists = artistIds.map { Artist(id = it, name = "Artist $it", photoUrl = null, tracks = null, games = null) },
        platform = Platform.OTHER,
        gameId = gameId,
    )
}
