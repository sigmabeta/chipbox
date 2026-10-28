package net.sigmabeta.chipbox.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.entities.ArtistEntity
import net.sigmabeta.chipbox.entities.GameEntity
import net.sigmabeta.chipbox.entities.TrackEntity

/**
 * The paged list queries must define a total order even when the display key ties.
 *
 * `FakeDatabase` cannot reproduce this: it sorts with a stable Kotlin comparator, so peers always
 * keep insertion order. Real SQLite leaves the order among equal sort keys unspecified, and the
 * browser walks pages with plain `OFFSET` — so a boundary inside a tie group is exactly where a
 * row can be repeated or skipped. These tests run against an in-memory Room/SQLite database and
 * assert that the concatenation of every page is the unpaged list, exactly once.
 *
 * Every fixture uses only a handful of distinct titles/names, so every page boundary falls inside
 * a tie group.
 */
class DaoPagingStabilityTest {

    private lateinit var database: ChipboxDatabase

    @BeforeTest
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder<ChipboxDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `game pages partition a table of duplicate titles`() = runTest {
        val dao = database.gameDao()
        repeat(GAME_COUNT) { index ->
            dao.insert(
                GameEntity(
                    // Only four distinct titles: every page boundary lands inside a tie group.
                    title = TITLES[index % TITLES.size],
                    photoUrl = null,
                    folderKey = "/library/$index",
                    folderSignature = "signature-$index",
                ),
            )
        }

        val full = dao.getAll().first()
        val walked = walk(pageSize = 37) { offset -> dao.getAllPaged(limit = 37, offset = offset).first() }

        assertEquals(full.map { it.id }, walked.map { it.id }, "pages must be the unpaged list in order")
        assertEquals(full.size, walked.toSet().size, "a game appeared on more than one page")
    }

    @Test
    fun `artist pages partition a table of case-distinct names`() = runTest {
        val dao = database.artistDao()
        repeat(ARTIST_COUNT) { index ->
            // Distinct rows under BINARY (the unique index) that all tie under COLLATE NOCASE.
            dao.insert(ArtistEntity(name = caseVariant(index)))
        }

        val full = dao.getAll().first()
        val walked = walk(pageSize = 11) { offset -> dao.getAllPaged(limit = 11, offset = offset).first() }

        assertEquals(full.map { it.id }, walked.map { it.id }, "pages must be the unpaged list in order")
        assertEquals(full.size, walked.toSet().size, "an artist appeared on more than one page")
    }

    @Test
    fun `track pages partition a table of duplicate titles`() = runTest {
        val dao = database.trackDao()
        val gameId = database.gameDao().insert(
            GameEntity(
                title = "Fixture Game",
                photoUrl = null,
                folderKey = "/library",
                folderSignature = "signature",
            ),
        )
        repeat(TRACK_COUNT) { index ->
            dao.insert(
                TrackEntity(
                    title = TITLES[index % TITLES.size],
                    path = "/library/$index.spc",
                    source = "file",
                    trackLengthMs = 1_000,
                    trackNumber = 0,
                    fadeLengthMs = 0,
                    gameId = gameId,
                ),
            )
        }

        val full = dao.getAll().first()
        val walked = walk(pageSize = 23) { offset -> dao.getAllPaged(limit = 23, offset = offset).first() }

        assertEquals(full.map { it.id }, walked.map { it.id }, "pages must be the unpaged list in order")
        assertEquals(full.size, walked.toSet().size, "a track appeared on more than one page")
    }

    private suspend fun <T : Any> walk(pageSize: Int, page: suspend (Int) -> List<T>): List<T> {
        val seen = mutableListOf<T>()
        repeat(MAX_PAGES) {
            val rows = page(seen.size)
            if (rows.isEmpty()) return seen
            assertTrue(rows.size <= pageSize, "a page exceeded its requested limit")
            seen += rows
        }
        error("paging did not terminate after $MAX_PAGES pages")
    }

    private companion object {
        const val GAME_COUNT = 200
        const val ARTIST_COUNT = 120
        const val TRACK_COUNT = 200
        const val MAX_PAGES = 10_000
        val TITLES = listOf("Same", "same", "SAME", "Same!")

        /**
         * 120 distinct case variants of a 7-letter name: all different under BINARY (so the unique
         * `artist.name` index accepts them) and all equal under COLLATE NOCASE (so they tie).
         */
        fun caseVariant(index: Int): String = "artistx".mapIndexed { bit, char ->
            if ((index shr bit) and 1 == 1) char.uppercaseChar() else char
        }.joinToString("")
    }
}
