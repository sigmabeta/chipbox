package net.sigmabeta.chipbox.features.home.modules

import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import net.sigmabeta.chipbox.features.home.HomeAction
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * Surfaces a single date-seeded "game of the day". The seed is derived from the current epoch-day
 * (UTC), so the pick is stable across in-day re-mounts and rolls over once per day. The section
 * emits exactly one item, which makes [net.sigmabeta.chipbox.features.home.HomeState] render it
 * full-width rather than wrapping it in a horizontal scroller.
 */
class GameOfTheDayHomeModule @Inject constructor(
    private val repository: Repository,
    private val scanner: Scanner,
    private val stringProvider: StringProvider,
) : HomeModule {

    override val id = ID
    override val priority = PRIORITY

    // Hold off on picking while a library scan is in flight — the game list churns as the scan
    // discovers content, so we'd be picking from a moving, partial set. The section stays hidden
    // until the scan settles, then we surface the day's game.
    override fun state(): Flow<LCE<HomeModuleSection>> = scanner.hideSectionWhileScanning(::content)

    private fun content(): Flow<LCE<HomeModuleSection>> = flow {
        emit(LCE.Loading(LOAD_OP))
        emit(pickOfTheDay())
    }.catch { emit(LCE.Error(LOAD_OP, it)) }

    // Pick one game deterministically for the day with a surgical count + offset fetch — no need to
    // load the whole catalog. The epoch-day seed makes the pick stable within a day and roll over
    // once per day. Hidden (Uninitialized) when the library is empty.
    private suspend fun pickOfTheDay(): LCE<HomeModuleSection> {
        val count = repository.getGameCount()
        if (count == 0) return LCE.Uninitialized
        val index = ((Clock.System.now().toEpochMilliseconds() / MILLIS_PER_DAY) % count).toInt()
        val pick = repository.getGameAtIndex(index) ?: return LCE.Uninitialized
        return sectionFrom(pick)
    }

    private fun sectionFrom(pick: Game): LCE<HomeModuleSection> = LCE.Content(
        HomeModuleSection(
            title = stringProvider.getString(ChipboxStringId.HOME_SECTION_GAME_OF_THE_DAY),
            items = persistentListOf(
                GridImageListModel(
                    dataId = pick.id + ID_OFFSET,
                    name = pick.title,
                    sourceInfo = pick.photoUrl,
                    imagePlaceholder = Icon.Album,
                    clickAction = HomeAction.GameClicked(pick.id),
                    aspectRatio = GAME_COVER_ASPECT_RATIO,
                    maxWidthDp = MAX_WIDTH_DP,
                ),
            ),
        ),
    )

    private companion object {
        const val ID = "game_of_the_day"
        const val PRIORITY = 100
        const val LOAD_OP = "home.game_of_the_day.load"

        // Keep this row's dataId disjoint from any other module's that also surfaces games.
        const val ID_OFFSET = 3_000_000_000L
        const val GAME_COVER_ASPECT_RATIO = 0.75f

        // Cap the full-width hero so it stays a reasonable size on tablets / desktop.
        const val MAX_WIDTH_DP = 400f
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
