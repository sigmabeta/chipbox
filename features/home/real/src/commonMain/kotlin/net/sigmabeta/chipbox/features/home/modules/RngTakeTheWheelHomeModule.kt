package net.sigmabeta.chipbox.features.home.modules

import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.sigmabeta.chipbox.features.home.HomeAction
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * "RNG Take the Wheel" row — three cards (Song / Game / Artist) that re-randomize on every tap
 * (the actual random pick happens in
 * [net.sigmabeta.chipbox.features.home.HomeViewModel.handleAction]). Sits at the bottom of the
 * Home screen.
 *
 * The cards themselves are static, but the row hides itself on an empty library: with no songs
 * to shuffle, "Random Song/Game/Artist" would all dead-end. We observe a single-track probe
 * ([Repository.getAllTracks] with `limit = 1`) rather than load the catalog, and re-emit as the
 * library fills or empties.
 */
class RngTakeTheWheelHomeModule @Inject constructor(
    private val repository: Repository,
    private val scanner: Scanner,
    private val stringProvider: StringProvider,
) : HomeModule {

    override val id = ID
    override val priority = PRIORITY

    // Hidden while a scan runs: the empty→non-empty probe flips mid-scan and the random pick would
    // draw from a partial library. Surface the cards once the scan settles.
    override fun state(): Flow<LCE<HomeModuleSection>> = scanner.hideSectionWhileScanning(::content)

    private fun content(): Flow<LCE<HomeModuleSection>> =
        repository.getAllTracks(limit = 1).map { tracks ->
            // Only Succeeded means the library has at least one song; Loading/Empty/Failed all keep
            // the row hidden rather than flashing static cards before the probe settles.
            when (tracks) {
                is Data.Succeeded -> LCE.Content(section())
                Data.Loading, Data.Empty, is Data.Failed -> LCE.Uninitialized
            }
        }

    private fun section() = HomeModuleSection(
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
    )

    private companion object {
        const val ID = "rng_take_the_wheel"
        const val PRIORITY = 1000
        const val SONG_DATA_ID = -1001L
        const val GAME_DATA_ID = -1002L
        const val ARTIST_DATA_ID = -1003L
    }
}
