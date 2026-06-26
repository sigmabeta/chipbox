package net.sigmabeta.chipbox.features.home.modules

import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.features.home.HomeAction
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.history.PlaybackHistoryRepository
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * "New to You" — a random selection of up to [MAX_ITEMS] games the user has never played. Play
 * counts live in a separate (history) database from the library, so this bridges the two: the
 * played-game ids come from [PlaybackHistoryRepository.playedGameIds], and the library returns a
 * bounded random sample of everything not in that set ([Repository.getUnplayedGames]). Both queries
 * are surgical — no whole-catalog load. Re-emits as plays are recorded (a freshly-played game drops
 * out) or the library changes.
 *
 * Hidden while a scan runs (the library is a moving, partial target) and once every game has been
 * played (nothing left to discover).
 */
class UnplayedGamesHomeModule @Inject constructor(
    private val repository: Repository,
    private val historyRepository: PlaybackHistoryRepository,
    private val scanner: Scanner,
    private val stringProvider: StringProvider,
) : HomeModule {

    override val id = ID
    override val priority = PRIORITY

    override fun state(): Flow<LCE<HomeModuleSection>> = scanner.hideSectionWhileScanning(::content)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun content(): Flow<LCE<HomeModuleSection>> =
        historyRepository.playedGameIds().flatMapLatest { playedIds ->
            repository.getUnplayedGames(playedIds, MAX_ITEMS).map { data ->
                when (data) {
                    Data.Loading -> LCE.Loading(LOAD_OP)
                    Data.Empty -> LCE.Uninitialized
                    is Data.Failed -> LCE.Error(LOAD_OP, IllegalStateException(data.message))
                    is Data.Succeeded -> section(data.data)
                }
            }
        }

    private fun section(games: List<Game>): LCE<HomeModuleSection> = LCE.Content(
        HomeModuleSection(
            title = stringProvider.getString(ChipboxStringId.HOME_SECTION_NEW_TO_YOU),
            items = games.map { game ->
                GridImageListModel(
                    dataId = game.id + ID_OFFSET,
                    name = game.title,
                    sourceInfo = game.photoUrl,
                    imagePlaceholder = Icon.Album,
                    clickAction = HomeAction.GameClicked(game.id),
                    aspectRatio = GAME_COVER_ASPECT_RATIO,
                )
            }.toImmutableList(),
        ),
    )

    private companion object {
        const val ID = "unplayed_games"
        const val PRIORITY = 600
        const val LOAD_OP = "home.unplayed_games.load"

        const val MAX_ITEMS = 10

        // Disjoint from other game-surfacing modules' dataIds.
        const val ID_OFFSET = 9_000_000_000L
        const val GAME_COVER_ASPECT_RATIO = 0.75f
    }
}
