package net.sigmabeta.chipbox.features.home.modules

import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.features.home.HomeAction
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
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
 * A random selection of games that entered the library within the last [WINDOW_MS] (one week). The
 * window filter, random pick, and the [MAX_ITEMS] cap all run in a single bounded SQL query
 * ([Repository.getRecentlyAddedGames]) — Home rows must render fast, so this never materializes the
 * whole catalog. Re-emits when the library changes.
 *
 * Hidden only when nothing falls inside the window — even a single fresh game surfaces, so small
 * library updates aren't swallowed.
 */
class RecentlyAddedGamesHomeModule @Inject constructor(
    private val repository: Repository,
    private val scanner: Scanner,
    private val stringProvider: StringProvider,
) : HomeModule {

    override val id = ID
    override val priority = PRIORITY

    // Hidden while a scan runs: getRecentlyAddedGames re-emits on every insert, so an ungated row
    // would fill in live as the scan discovers games. Surface it once the scan settles.
    override fun state(): Flow<LCE<HomeModuleSection>> = scanner.hideSectionWhileScanning(::content)

    private fun content(): Flow<LCE<HomeModuleSection>> =
        repository.getRecentlyAddedGames(MAX_ITEMS, WINDOW_MS).map { data ->
            when (data) {
                Data.Loading -> LCE.Loading(LOAD_OP)
                Data.Empty -> LCE.Uninitialized
                is Data.Failed -> LCE.Error(LOAD_OP, IllegalStateException(data.message))
                is Data.Succeeded -> section(data.data)
            }
        }

    private fun section(games: List<Game>): LCE<HomeModuleSection> = LCE.Content(
        HomeModuleSection(
            title = stringProvider.getString(ChipboxStringId.HOME_SECTION_RECENTLY_ADDED),
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
        const val ID = "recently_added_games"
        const val PRIORITY = 150
        const val LOAD_OP = "home.recently_added_games.load"

        const val MAX_ITEMS = 10

        // Disjoint from other game/song-surfacing modules' dataIds.
        const val ID_OFFSET = 8_000_000_000L
        const val GAME_COVER_ASPECT_RATIO = 0.75f

        // Freshness window: only surface games added within the last week.
        const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    }
}
