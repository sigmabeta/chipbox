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
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.repository.Data
import net.sigmabeta.chipbox.repository.Repository
import net.sigmabeta.chipbox.scanner.Scanner
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
    private val scanner: Scanner,
    private val stringProvider: StringProvider,
) : HomeModule {

    override val id = ID
    override val priority = PRIORITY

    // Hidden while a scan runs: the library join (getArtistsByIds) re-emits as artists hydrate, so
    // the row would flicker mid-scan. Surface it once the scan settles.
    override fun state(): Flow<LCE<HomeModuleSection>> = scanner.hideSectionWhileScanning(::content)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun content(): Flow<LCE<HomeModuleSection>> =
        historyRepository.mostPlayedArtists(QUERY_LIMIT).flatMapLatest { counts ->
            if (counts.isEmpty()) {
                flowOf<LCE<HomeModuleSection>>(LCE.Uninitialized)
            } else {
                // Resolve only the most-played artist ids (for name + photo) rather than hydrating
                // the entire catalog to look a handful of them up.
                repository.getArtistsByIds(counts.map { it.id }).map { artistsData ->
                    when (artistsData) {
                        Data.Loading -> LCE.Loading(LOAD_OP)

                        Data.Empty -> LCE.Uninitialized

                        is Data.Failed -> LCE.Error(LOAD_OP, IllegalStateException(artistsData.message))

                        is Data.Succeeded -> {
                            val byId = artistsData.data.associateBy { it.id }
                            sectionOrHidden(counts.mapNotNull { byId[it.id] })
                        }
                    }
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
