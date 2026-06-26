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
 * Most-played songs, highest play-count first. Joins the history's per-song counts against the
 * library's tracks (for title + game art). Hidden until at least [MIN_ITEMS] tracks have been
 * played more than once.
 */
class MostPlayedSongsHomeModule @Inject constructor(
    private val repository: Repository,
    private val historyRepository: PlaybackHistoryRepository,
    private val scanner: Scanner,
    private val stringProvider: StringProvider,
) : HomeModule {

    override val id = ID
    override val priority = PRIORITY

    // Hidden while a scan runs: the library join (getTracksByIds) re-emits as tracks hydrate, so the
    // row would flicker mid-scan. Surface it once the scan settles.
    override fun state(): Flow<LCE<HomeModuleSection>> = scanner.hideSectionWhileScanning(::content)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun content(): Flow<LCE<HomeModuleSection>> =
        historyRepository.mostPlayedSongs(QUERY_LIMIT).flatMapLatest { counts ->
            if (counts.isEmpty()) {
                flowOf<LCE<HomeModuleSection>>(LCE.Uninitialized)
            } else {
                // Resolve only the most-played track ids (for title + game art) rather than
                // hydrating the entire library to look a handful of them up.
                repository.getTracksByIds(counts.map { it.id }, withGame = true).map { tracksData ->
                    when (tracksData) {
                        Data.Loading -> LCE.Loading(LOAD_OP)

                        Data.Empty -> LCE.Uninitialized

                        is Data.Failed -> LCE.Error(LOAD_OP, IllegalStateException(tracksData.message))

                        is Data.Succeeded -> {
                            val byId = tracksData.data.associateBy { it.id }
                            sectionOrHidden(counts.mapNotNull { byId[it.id] })
                        }
                    }
                }
            }
        }

    private fun sectionOrHidden(tracks: List<Track>): LCE<HomeModuleSection> {
        if (tracks.size < MIN_ITEMS) return LCE.Uninitialized
        return LCE.Content(
            HomeModuleSection(
                title = stringProvider.getString(ChipboxStringId.HOME_SECTION_MOST_PLAYED_SONGS),
                items = tracks.map { track ->
                    GridImageListModel(
                        dataId = track.id + ID_OFFSET,
                        name = track.title,
                        sourceInfo = track.game?.photoUrl,
                        imagePlaceholder = Icon.MusicNote,
                        clickAction = HomeAction.SongClicked(track.id),
                        aspectRatio = SONG_COVER_ASPECT_RATIO,
                    )
                }.toImmutableList(),
            ),
        )
    }

    private companion object {
        const val ID = "most_played_songs"
        const val PRIORITY = 300
        const val LOAD_OP = "home.most_played_songs.load"

        const val QUERY_LIMIT = 10
        const val MIN_ITEMS = 3

        const val ID_OFFSET = 5_000_000_000L
        const val SONG_COVER_ASPECT_RATIO = 0.75f
    }
}
