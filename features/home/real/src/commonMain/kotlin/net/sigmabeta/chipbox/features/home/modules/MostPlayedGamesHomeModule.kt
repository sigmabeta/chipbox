package net.sigmabeta.chipbox.features.home.modules

import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.features.home.HomeAction
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.history.PlaybackHistoryRepository
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * Most-played games, highest play-count first. Joins the history's per-game counts against the
 * library's games (for title + cover art). Hidden until at least [MIN_ITEMS] games have been
 * played more than once.
 */
class MostPlayedGamesHomeModule @Inject constructor(
    private val repository: Repository,
    private val historyRepository: PlaybackHistoryRepository,
    private val stringProvider: StringProvider,
) : HomeModule {

    override val id = ID
    override val priority = PRIORITY

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun state(): Flow<LCE<HomeModuleSection>> =
        historyRepository.mostPlayedGames(QUERY_LIMIT).flatMapLatest { counts ->
            if (counts.isEmpty()) {
                flowOf<LCE<HomeModuleSection>>(LCE.Uninitialized)
            } else {
                // Resolve only the most-played game ids (for title + cover art) rather than
                // hydrating the entire catalog to look a handful of them up.
                repository.getGamesByIds(counts.map { it.id }).map { gamesData ->
                    when (gamesData) {
                        Data.Loading -> LCE.Loading(LOAD_OP)

                        Data.Empty -> LCE.Uninitialized

                        is Data.Failed -> LCE.Error(LOAD_OP, IllegalStateException(gamesData.message))

                        is Data.Succeeded -> {
                            val byId = gamesData.data.associateBy { it.id }
                            sectionOrHidden(counts.mapNotNull { byId[it.id] })
                        }
                    }
                }
            }
        }

    private fun sectionOrHidden(games: List<Game>): LCE<HomeModuleSection> {
        if (games.size < MIN_ITEMS) return LCE.Uninitialized
        return LCE.Content(
            HomeModuleSection(
                title = stringProvider.getString(ChipboxStringId.HOME_SECTION_MOST_PLAYED_GAMES),
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
    }

    private companion object {
        const val ID = "most_played_games"
        const val PRIORITY = 400
        const val LOAD_OP = "home.most_played_games.load"

        const val QUERY_LIMIT = 10
        const val MIN_ITEMS = 3

        const val ID_OFFSET = 6_000_000_000L
        const val GAME_COVER_ASPECT_RATIO = 0.75f
    }
}
