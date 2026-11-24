package net.sigmabeta.chipbox.features.home.modules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.features.home.module.HomeDay
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider

class GameOfTheDayHomeModuleTest {

    @Test
    fun `picks the game at the day's index`() = runTest {
        val lce = moduleFor(day = 12, count = 5).state().first { it !is LCE.Loading }

        assertTrue(lce is LCE.Content)
        assertEquals(listOf(2L + ID_OFFSET), lce.data.items.map { it.dataId })
    }

    @Test
    fun `a fixed day picks the same game every time`() = runTest {
        val picks = List(3) {
            (moduleFor(day = 20_781, count = 7).state().first { it !is LCE.Loading } as LCE.Content).data.items
        }

        assertEquals(List(3) { picks.first() }, picks)
    }

    @Test
    fun `hidden when the library is empty`() = runTest {
        assertEquals(LCE.Uninitialized, moduleFor(day = 1, count = 0).state().first { it !is LCE.Loading })
    }

    @Test
    fun `shows Loading before the pick`() = runTest {
        assertEquals(LCE.Loading("home.game_of_the_day.load"), moduleFor(day = 1, count = 3).state().first())
    }

    private fun moduleFor(day: Long, count: Int) = GameOfTheDayHomeModule(
        repoWith(count),
        CountingScanner(UnconfinedTestDispatcher()),
        stubStringProvider(),
        HomeDay.fixed(day),
    )

    // Game ids equal their index, so the picked id is the index the module asked for.
    private fun repoWith(count: Int): Repository = object : Repository by FakeRepository(emptyMap()) {
        override suspend fun getGameCount() = count
        override suspend fun getGameAtIndex(index: Int) = if (index in 0 until count) gameOf(index.toLong()) else null
    }

    private fun gameOf(id: Long) = Game(id = id, title = "g$id", photoUrl = null, artists = null, tracks = null)

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }

    private companion object {
        const val ID_OFFSET = 3_000_000_000L
    }
}
