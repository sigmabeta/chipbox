package net.sigmabeta.chipbox.features.home.modules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider

class RecentlyAddedGamesHomeModuleTest {

    @Test
    fun `hidden while the query is loading`() = runTest {
        val module = moduleWith(flowOf(Data.Loading))
        assertEquals(LCE.Loading("home.recently_added_games.load"), module.state().first())
    }

    @Test
    fun `hidden when nothing falls inside the window`() = runTest {
        val module = moduleWith(flowOf(Data.Empty))
        assertEquals(LCE.Uninitialized, module.state().first())
    }

    @Test
    fun `surfaces a single recently-added game without hiding it`() = runTest {
        val module = moduleWith(flowOf(Data.Succeeded(listOf(gameOf(1L)))))

        val lce = module.state().first()
        assertTrue(lce is LCE.Content)
        assertEquals(listOf(1L + ID_OFFSET), lce.data.items.map { it.dataId })
    }

    @Test
    fun `maps every game the repository returns into a card`() = runTest {
        val games = (1L..5L).map { gameOf(it) }
        val module = moduleWith(flowOf(Data.Succeeded(games)))

        val lce = module.state().first()
        assertTrue(lce is LCE.Content)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), lce.data.items.map { it.dataId - ID_OFFSET })
    }

    private fun moduleWith(flow: Flow<Data<List<Game>>>) =
        RecentlyAddedGamesHomeModule(repoWith(flow), stubStringProvider())

    private fun repoWith(flow: Flow<Data<List<Game>>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getRecentlyAddedGames(limit: Int, withinMs: Long) = flow
        }

    private fun gameOf(id: Long) = Game(id = id, title = "g$id", photoUrl = null, artists = null, tracks = null)

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }

    private companion object {
        const val ID_OFFSET = 8_000_000_000L
    }
}
