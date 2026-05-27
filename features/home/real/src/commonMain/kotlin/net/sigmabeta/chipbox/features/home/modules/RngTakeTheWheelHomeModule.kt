package net.sigmabeta.chipbox.features.home.modules

import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.sigmabeta.chipbox.features.home.HomeAction
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * Static "RNG Take the Wheel" row — three cards (Song / Game / Artist) that re-randomize on
 * every tap (the actual random pick happens in
 * [net.sigmabeta.chipbox.features.home.HomeViewModel.handleAction]). Sits at the bottom of
 * the Home screen.
 */
class RngTakeTheWheelHomeModule @Inject constructor(
    private val stringProvider: StringProvider,
) : HomeModule {

    override val id = ID
    override val priority = PRIORITY

    override fun state(): Flow<LCE<HomeModuleSection>> = flowOf(
        LCE.Content(
            HomeModuleSection(
                title = stringProvider.getString(ChipboxStringId.HOME_SECTION_RNG),
                items = persistentListOf(
                    GridImageListModel(
                        dataId = SONG_DATA_ID,
                        name = stringProvider.getString(ChipboxStringId.HOME_RNG_RANDOM_SONG),
                        sourceInfo = null,
                        imagePlaceholder = Icon.MusicNote,
                        clickAction = HomeAction.RandomSongClicked,
                    ),
                    GridImageListModel(
                        dataId = GAME_DATA_ID,
                        name = stringProvider.getString(ChipboxStringId.HOME_RNG_RANDOM_GAME),
                        sourceInfo = null,
                        imagePlaceholder = Icon.Album,
                        clickAction = HomeAction.RandomGameClicked,
                    ),
                    GridImageListModel(
                        dataId = ARTIST_DATA_ID,
                        name = stringProvider.getString(ChipboxStringId.HOME_RNG_RANDOM_ARTIST),
                        sourceInfo = null,
                        imagePlaceholder = Icon.Person,
                        clickAction = HomeAction.RandomArtistClicked,
                    ),
                ),
            ),
        ),
    )

    private companion object {
        const val ID = "rng_take_the_wheel"
        const val PRIORITY = 1000
        const val SONG_DATA_ID = -1001L
        const val GAME_DATA_ID = -1002L
        const val ARTIST_DATA_ID = -1003L
    }
}
