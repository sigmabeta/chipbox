package net.sigmabeta.chipbox.features.favorites

import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.HorizontalScrollerListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.NameCaptionValueListModel
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.components.WideItemListModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class FavoritesState(
    val tracks: LCE<List<Track>> = LCE.Content(emptyList()),
    val games: LCE<List<Game>> = LCE.Content(emptyList()),
    val artists: LCE<List<Artist>> = LCE.Content(emptyList()),
    val playingTrackId: Long? = null,
) : ListState() {
    override val columnType: ColumnType = ColumnType.Staggered(STAGGERED_WIDTH_DP, false)

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.LIBRARY_FAVORITES),
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = if (isEverythingEmpty()) {
        listOf(
            EmptyStateListModel(
                icon = Icon.FavoriteEmpty,
                explanation = stringProvider.getString(ChipboxStringId.LIBRARY_FAVORITES_EMPTY),
            )
        )
    } else {
        listOf(
            tracksSection(stringProvider),
            gamesSection(stringProvider),
            artistsSection(stringProvider),
        )
    }

    private fun tracksSection(stringProvider: StringProvider) = tracks.sectionWithStandardErrorAndLoading(
        sectionName = SECTION_NAME_TRACKS,
        loadingType = LoadingType.TEXT_CAPTION,
        loadingItemCount = TRACKS_LOADING_COUNT,
        loadingWithHeader = true,
    ) {
        if (data.isEmpty()) {
            emptyList()
        } else {
            listOf(
                SectionHeaderListModel(
                    stringProvider.getString(ChipboxStringId.FAVORITES_SECTION_TRACKS),
                ),
            ) + data.mapIndexed(::trackRow)
        }
    }

    private fun trackRow(index: Int, track: Track): ListModel = NameCaptionValueListModel(
        dataId = track.id + ID_PREFIX_TRACKS,
        name = track.title,
        caption = track.game?.title.orEmpty(),
        value = formatTrackLength(track.trackLengthMs),
        clickAction = FavoritesAction.TrackClicked(index),
        active = track.id == playingTrackId,
    )

    private fun gamesSection(stringProvider: StringProvider) = games.sectionWithStandardErrorAndLoading(
        sectionName = SECTION_NAME_GAMES,
        loadingType = LoadingType.WIDE_ITEM,
        loadingWithHeader = true,
        loadingHorizScrollable = true,
    ) {
        if (data.isEmpty()) {
            emptyList()
        } else {
            listOf(
                SectionHeaderListModel(
                    stringProvider.getString(ChipboxStringId.FAVORITES_SECTION_GAMES),
                ),
                HorizontalScrollerListModel(
                    dataId = SECTION_NAME_GAMES.hashCode().toLong() + ID_PREFIX_SCROLLER,
                    scrollingItems = data.map { game ->
                        WideItemListModel(
                            dataId = game.id + ID_PREFIX_GAMES,
                            name = game.title,
                            sourceInfo = game.photoUrl,
                            imagePlaceholder = Icon.Album,
                            clickAction = FavoritesAction.GameClicked(game.id),
                        )
                    }.toImmutableList(),
                ),
            )
        }
    }

    private fun artistsSection(stringProvider: StringProvider) = artists.sectionWithStandardErrorAndLoading(
        sectionName = SECTION_NAME_ARTISTS,
        loadingType = LoadingType.WIDE_ITEM,
        loadingWithHeader = true,
        loadingHorizScrollable = true,
    ) {
        if (data.isEmpty()) {
            emptyList()
        } else {
            listOf(
                SectionHeaderListModel(
                    stringProvider.getString(ChipboxStringId.FAVORITES_SECTION_ARTISTS),
                ),
                HorizontalScrollerListModel(
                    dataId = SECTION_NAME_ARTISTS.hashCode().toLong() + ID_PREFIX_SCROLLER,
                    scrollingItems = data.map { artist ->
                        WideItemListModel(
                            dataId = artist.id + ID_PREFIX_ARTISTS,
                            name = artist.name,
                            sourceInfo = artist.photoUrl,
                            imagePlaceholder = Icon.Person,
                            clickAction = FavoritesAction.ArtistClicked(artist.id),
                        )
                    }.toImmutableList(),
                ),
            )
        }
    }

    /**
     * True only when all three collections have loaded and are empty — the cue to show the single
     * "nothing favorited yet" empty state instead of three blank sections. A section still loading
     * or in error keeps the per-section skeleton/error path.
     */
    private fun isEverythingEmpty(): Boolean {
        val loadedTracks = (tracks as? LCE.Content)?.data ?: return false
        val loadedGames = (games as? LCE.Content)?.data ?: return false
        val loadedArtists = (artists as? LCE.Content)?.data ?: return false
        return loadedTracks.isEmpty() && loadedGames.isEmpty() && loadedArtists.isEmpty()
    }

    private fun formatTrackLength(millis: Long): String {
        val totalSeconds = millis / MILLIS_PER_SECOND
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    companion object {
        private const val SECTION_NAME_TRACKS = "section.tracks"
        private const val SECTION_NAME_GAMES = "section.games"
        private const val SECTION_NAME_ARTISTS = "section.artists"

        private const val STAGGERED_WIDTH_DP = 320
        private const val TRACKS_LOADING_COUNT = 8

        private const val MILLIS_PER_SECOND = 1_000L
        private const val SECONDS_PER_MINUTE = 60L

        private const val ID_PREFIX_TRACKS = 1_000_000L
        private const val ID_PREFIX_GAMES = 1_000_000_000L
        private const val ID_PREFIX_ARTISTS = 2_000_000_000L
        private const val ID_PREFIX_SCROLLER = 1_000_000_000_000L
    }
}
