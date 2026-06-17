package net.sigmabeta.chipbox.features.home.modules

import dev.zacsweers.metro.Inject
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import net.sigmabeta.chipbox.features.home.HomeAction
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.scanner.state.ScannerState
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * Surfaces a date-seeded random pick of games. The seed is derived from the current epoch-day
 * (UTC), so the selection is stable across in-day re-mounts and rolls over once per day.
 */
class RandomGamesHomeModule @Inject constructor(
    private val repository: Repository,
    private val scanner: Scanner,
    private val stringProvider: StringProvider,
) : HomeModule {

    override val id = ID
    override val priority = PRIORITY

    // Hold off on loading games while a library scan is in flight — the game list churns as the
    // scan discovers content, so we'd be shuffling a moving, partial set. Stay in Loading until the
    // scan settles, then switch to the repository and surface the day's picks. distinctUntilChanged
    // on the scanning flag keeps us from re-subscribing on every Scanning progress emission.
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun state(): Flow<LCE<HomeModuleSection>> = scanner.state()
        .map { it is ScannerState.Scanning }
        .distinctUntilChanged()
        .flatMapLatest { isScanning ->
            if (isScanning) {
                flowOf(LCE.Loading(LOAD_OP))
            } else {
                repository.getAllGames().map { data ->
                    when (data) {
                        Data.Loading -> LCE.Loading(LOAD_OP)
                        Data.Empty -> LCE.Content(sectionFrom(emptyList()))
                        is Data.Succeeded -> LCE.Content(sectionFrom(data.data))
                        is Data.Failed -> LCE.Error(LOAD_OP, IllegalStateException(data.message))
                    }
                }
            }
        }
        .onStart { emit(LCE.Loading(LOAD_OP)) }

    private fun sectionFrom(games: List<Game>): HomeModuleSection {
        val seed = Clock.System.now().toEpochMilliseconds() / MILLIS_PER_DAY
        val picks = games.shuffled(Random(seed)).take(RANDOM_COUNT)
        return HomeModuleSection(
            title = stringProvider.getString(ChipboxStringId.HOME_SECTION_RANDOM_GAMES),
            items = picks.map { game ->
                GridImageListModel(
                    dataId = game.id + ID_OFFSET,
                    name = game.title,
                    sourceInfo = game.photoUrl,
                    imagePlaceholder = Icon.Album,
                    clickAction = HomeAction.GameClicked(game.id),
                    aspectRatio = GAME_COVER_ASPECT_RATIO,
                )
            }.toImmutableList(),
        )
    }

    private companion object {
        const val ID = "random_games"
        const val PRIORITY = 100
        const val LOAD_OP = "home.random_games.load"
        const val RANDOM_COUNT = 12

        // Keep this row's dataIds disjoint from any other module's that also surfaces games.
        const val ID_OFFSET = 3_000_000_000L
        const val GAME_COVER_ASPECT_RATIO = 0.75f
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
