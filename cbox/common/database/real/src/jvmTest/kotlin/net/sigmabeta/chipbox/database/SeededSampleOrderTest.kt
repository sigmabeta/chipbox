package net.sigmabeta.chipbox.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.database.dao.seededOrderKey
import net.sigmabeta.chipbox.entities.GameEntity

/**
 * The sampled Home rows (`getRecentlyAdded`, `getUnplayed`) order by `SEEDED_ORDER` against real
 * SQLite: one seed gives one order, another seed a different one, and the order is exactly
 * [seededOrderKey]'s, which `FakeDatabase` relies on to mirror these queries.
 */
class SeededSampleOrderTest {

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
    fun `one seed gives the same sample every query`() = runTest {
        insertGames()
        val dao = database.gameDao()

        val first = dao.getRecentlyAdded(threshold = 0, limit = LIMIT, seed = SEED).first().ids()
        val second = dao.getRecentlyAdded(threshold = 0, limit = LIMIT, seed = SEED).first().ids()

        assertEquals(first, second)
    }

    @Test
    fun `different seeds give different samples`() = runTest {
        insertGames()
        val dao = database.gameDao()

        val one = dao.getUnplayed(playedIds = emptyList(), limit = LIMIT, seed = SEED).first().ids()
        val other = dao.getUnplayed(playedIds = emptyList(), limit = LIMIT, seed = OTHER_SEED).first().ids()

        assertNotEquals(one, other)
    }

    @Test
    fun `SQLite orders exactly as seededOrderKey`() = runTest {
        val ids = insertGames()
        val dao = database.gameDao()

        for (seed in listOf(0L, SEED, OTHER_SEED, MAX_SEED)) {
            val expected = ids.sortedWith(compareBy({ seededOrderKey(it, seed) }, { it }))
            val actual = dao.getUnplayed(playedIds = emptyList(), limit = ids.size, seed = seed).first().ids()
            assertEquals(expected, actual, "order for seed $seed")
        }
    }

    // Ordinary small ids plus ids near 2^31, where the hash's product is largest.
    private suspend fun insertGames(): List<Long> {
        val dao = database.gameDao()
        val explicit = listOf(MAX_SEED, MAX_SEED - 1, MAX_SEED / 2)
        val small = (1..GAME_COUNT).map { dao.insert(game("small-$it")) }
        val large = explicit.map { dao.insert(game("large-$it", id = it)) }
        return small + large
    }

    private fun game(key: String, id: Long = 0) = GameEntity(
        title = key,
        photoUrl = null,
        folderKey = "/library/$key",
        folderSignature = "signature-$key",
        dateAdded = 1,
        id = id,
    )

    private fun List<GameEntity>.ids() = map { it.id }

    private companion object {
        const val GAME_COUNT = 40
        const val LIMIT = 10
        const val SEED = 20_261_124L
        const val OTHER_SEED = 7L
        const val MAX_SEED = (1L shl 31) - 1
    }
}
