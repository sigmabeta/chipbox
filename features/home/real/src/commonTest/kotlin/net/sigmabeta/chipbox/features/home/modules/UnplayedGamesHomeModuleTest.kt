package net.sigmabeta.chipbox.features.home.modules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.sigmabeta.chipbox.history.fake.FakePlaybackHistoryRepository
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.repository.fake.FakeRepository
import net.sigmabeta.chipbox.scanner.fake.CountingScanner
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.ui.SageStringId
import net.sigmabeta.sage.ui.StringProvider

class UnplayedGamesHomeModuleTest {

    @Test
    fun `hidden while the query is loading`() = runTest {
        val module = moduleWith(flowOf(Data.Loading))
        assertEquals(LCE.Loading("home.unplayed_games.load"), module.state().first())
    }

    @Test
    fun `hidden when every game has been played`() = runTest {
        val module = moduleWith(flowOf(Data.Empty))
        assertEquals(LCE.Uninitialized, module.state().first())
    }

    @Test
    fun `maps every unplayed game the repository returns into a card`() = runTest {
        val games = (1L..3L).map { gameOf(it) }
        val module = moduleWith(flowOf(Data.Succeeded(games)))

        val lce = module.state().first()
        assertTrue(lce is LCE.Content)
        assertEquals(listOf(1L, 2L, 3L), lce.data.items.map { it.dataId - ID_OFFSET })
    }

    @Test
    fun `forwards the played game ids to the repository as the exclusion set`() = runTest {
        var captured: List<Long>? = null
        val repo = object : Repository by FakeRepository(emptyMap()) {
            override fun getUnplayedGames(playedGameIds: List<Long>, limit: Int): Flow<Data<List<Game>>> {
                captured = playedGameIds
                return flowOf(Data.Succeeded(listOf(gameOf(7L))))
            }
        }
        val history = FakePlaybackHistoryRepository().apply { playedGameIds = listOf(1L, 2L) }
        val module = UnplayedGamesHomeModule(repo, history, idleScanner(), stubStringProvider())

        module.state().first()
        assertEquals(listOf(1L, 2L), captured)
    }

    @Test
    fun `hidden while a scan is in flight`() = runTest {
        val scanner = CountingScanner(UnconfinedTestDispatcher(testScheduler))
        scanner.pushState(ScannerState.Scanning())
        val module = UnplayedGamesHomeModule(
            repoWith(flowOf(Data.Succeeded(listOf(gameOf(1L))))),
            FakePlaybackHistoryRepository(),
            scanner,
            stubStringProvider(),
        )
        assertEquals(LCE.Uninitialized, module.state().first())
    }

    private fun moduleWith(flow: Flow<Data<List<Game>>>) =
        UnplayedGamesHomeModule(repoWith(flow), FakePlaybackHistoryRepository(), idleScanner(), stubStringProvider())

    private fun repoWith(flow: Flow<Data<List<Game>>>): Repository =
        object : Repository by FakeRepository(emptyMap()) {
            override fun getUnplayedGames(playedGameIds: List<Long>, limit: Int) = flow
        }

    private fun idleScanner() = CountingScanner(UnconfinedTestDispatcher())

    private fun gameOf(id: Long) = Game(id = id, title = "g$id", photoUrl = null, artists = null, tracks = null)

    private fun stubStringProvider() = object : StringProvider {
        override fun getString(string: SageStringId): String = string.toString()
        override fun getStringOneArg(string: SageStringId, arg: String): String = string.toString()
        override fun getStringOneInt(string: SageStringId, arg: Int): String = string.toString()
        override fun getStringTwoArgs(string: SageStringId, first: String, second: String): String = string.toString()
    }

    private companion object {
        const val ID_OFFSET = 9_000_000_000L
    }
}
