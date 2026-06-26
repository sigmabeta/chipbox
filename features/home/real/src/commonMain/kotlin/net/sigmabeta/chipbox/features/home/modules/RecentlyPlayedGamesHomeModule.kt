package net.sigmabeta.chipbox.features.home.modules

import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
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
import net.sigmabeta.chipbox.history.RecentPlay
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * Recently-played games, newest first. History records individual song plays, so this collapses the
 * recent-play stream to the distinct games those songs belong to (keeping each game's most-recent
 * play position), then joins against the library for title + cover art. Re-emits whenever a new play
 * is recorded or the library changes.
 *
 * Hidden unless there are at least [MIN_ITEMS] distinct games AND at least one song was played
 * within the last [MAX_AGE_MS] (4 days), so the row reflects current listening and quietly retires
 * once it goes stale — matching VGLS's RecentSongsModule freshness rule.
 */
class RecentlyPlayedGamesHomeModule @Inject constructor(
    private val repository: Repository,
    private val historyRepository: PlaybackHistoryRepository,
    private val scanner: Scanner,
    private val stringProvider: StringProvider,
) : HomeModule {

    override val id = ID
    override val priority = PRIORITY

    // Hidden while a scan runs: the library join (getTracksByIds) re-emits as games hydrate, so the
    // row would flicker mid-scan. Surface it once the scan settles.
    override fun state(): Flow<LCE<HomeModuleSection>> = scanner.hideSectionWhileScanning(::content)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun content(): Flow<LCE<HomeModuleSection>> =
        historyRepository.recentlyPlayed(QUERY_LIMIT).flatMapLatest { recents ->
            // With no plays the row stays hidden instead of flashing a loading scroller.
            if (recents.isEmpty()) {
                flowOf<LCE<HomeModuleSection>>(LCE.Uninitialized)
            } else {
                // Resolve only the recently-played track ids rather than the whole library.
                repository.getTracksByIds(recents.map { it.trackId }, withGame = true).map { tracksData ->
                    when (tracksData) {
                        Data.Loading -> LCE.Loading(LOAD_OP)

                        Data.Empty -> LCE.Uninitialized

                        is Data.Failed -> LCE.Error(LOAD_OP, IllegalStateException(tracksData.message))

                        is Data.Succeeded -> {
                            val trackById = tracksData.data.associateBy { it.id }
                            sectionOrHidden(recentGames(recents, trackById), hasRecentPlay(recents))
                        }
                    }
                }
            }
        }

    // Collapse the recent track plays to their distinct games, preserving newest-first order (the
    // first time a game appears wins, since `recents` is already newest-first). Capped at MAX_ITEMS.
    private fun recentGames(recents: List<RecentPlay>, trackById: Map<Long, Track>): List<Game> {
        val seen = LinkedHashMap<Long, Game>()
        for (recent in recents) {
            val game = trackById[recent.trackId]?.game ?: continue
            if (!seen.containsKey(game.id)) seen[game.id] = game
            if (seen.size >= MAX_ITEMS) break
        }
        return seen.values.toList()
    }

    // True when at least one recent play landed within the freshness window. The recent-play rows
    // are newest-first, so this is really "is the newest play younger than MAX_AGE_MS".
    @OptIn(ExperimentalTime::class)
    private fun hasRecentPlay(recents: List<RecentPlay>): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        return recents.any { now - it.timeMs <= MAX_AGE_MS }
    }

    private fun sectionOrHidden(games: List<Game>, hasRecentPlay: Boolean): LCE<HomeModuleSection> {
        if (!hasRecentPlay || games.size < MIN_ITEMS) return LCE.Uninitialized
        return LCE.Content(
            HomeModuleSection(
                title = stringProvider.getString(ChipboxStringId.HOME_SECTION_RECENTLY_PLAYED),
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
        const val ID = "recently_played_games"
        const val PRIORITY = 200
        const val LOAD_OP = "home.recently_played_games.load"

        // Pull a generous window of recent track plays since many can collapse into the same game.
        const val QUERY_LIMIT = 40
        const val MAX_ITEMS = 6
        const val MIN_ITEMS = 2

        // Disjoint from other game/song-surfacing modules' dataIds.
        const val ID_OFFSET = 4_000_000_000L
        const val GAME_COVER_ASPECT_RATIO = 0.75f

        // Freshness window: hide the row unless something was played within the last 4 days.
        const val MAX_AGE_MS = 4L * 24 * 60 * 60 * 1000
    }
}
