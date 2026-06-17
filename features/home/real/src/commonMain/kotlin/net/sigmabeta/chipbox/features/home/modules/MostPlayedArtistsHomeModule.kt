package net.sigmabeta.chipbox.features.home.modules

import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import net.sigmabeta.chipbox.features.home.HomeAction
import net.sigmabeta.chipbox.features.home.module.HomeModule
import net.sigmabeta.chipbox.features.home.module.HomeModuleSection
import net.sigmabeta.chipbox.history.PlaybackHistoryRepository
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * Most-played artists, highest play-count first. Joins the history's per-artist counts against the
 * library's artists (for name + photo). Hidden until at least [MIN_ITEMS] artists have been played
 * more than once. Artist cells are square (the default 1f aspect), unlike the 3:4 game covers.
 */
class MostPlayedArtistsHomeModule @Inject constructor(
    private val repository: Repository,
    private val historyRepository: PlaybackHistoryRepository,
    private val stringProvider: StringProvider,
) : HomeModule {

    override val id = ID
    override val priority = PRIORITY

    override fun state(): Flow<LCE<HomeModuleSection>> = combine(
        historyRepository.mostPlayedArtists(QUERY_LIMIT),
        repository.getAllArtists(),
    ) { counts, artistsData ->
        when (artistsData) {
            Data.Loading -> if (counts.isEmpty()) LCE.Uninitialized else LCE.Loading(LOAD_OP)

            Data.Empty -> LCE.Uninitialized

            is Data.Failed -> LCE.Error(LOAD_OP, IllegalStateException(artistsData.message))

            is Data.Succeeded -> {
                val byId = artistsData.data.associateBy { it.id }
                val artists = counts.mapNotNull { byId[it.id] }
                sectionOrHidden(artists)
            }
        }
    }

    private fun sectionOrHidden(artists: List<Artist>): LCE<HomeModuleSection> {
        if (artists.size < MIN_ITEMS) return LCE.Uninitialized
        return LCE.Content(
            HomeModuleSection(
                title = stringProvider.getString(ChipboxStringId.HOME_SECTION_MOST_PLAYED_ARTISTS),
                items = artists.map { artist ->
                    GridImageListModel(
                        dataId = artist.id + ID_OFFSET,
                        name = artist.name,
                        sourceInfo = artist.photoUrl,
                        imagePlaceholder = Icon.Person,
                        clickAction = HomeAction.ArtistClicked(artist.id),
                    )
                }.toImmutableList(),
            ),
        )
    }

    private companion object {
        const val ID = "most_played_artists"
        const val PRIORITY = 500
        const val LOAD_OP = "home.most_played_artists.load"

        const val QUERY_LIMIT = 10
        const val MIN_ITEMS = 3

        const val ID_OFFSET = 7_000_000_000L
    }
}
