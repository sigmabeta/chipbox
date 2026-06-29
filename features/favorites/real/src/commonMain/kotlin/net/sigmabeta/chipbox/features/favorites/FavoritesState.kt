package net.sigmabeta.chipbox.features.favorites

import net.sigmabeta.chipbox.models.Artist
import net.sigmabeta.chipbox.models.Game
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.GridImageListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.NameCaptionValueListModel
import net.sigmabeta.sage.components.SectionHeaderListModel
import net.sigmabeta.sage.components.TitleBarModel
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
    // A single vertical grid: full-width section headers and track rows span every lane, while
    // game and artist cells tile into single lanes. No horizontal scrollers — the whole screen
    // scrolls vertically only.
    override val columnType: ColumnType = ColumnType.Regular(GRID_CELL_WIDTH_DP)

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
        tracksSection(stringProvider) +
            gamesSection(stringProvider) +
            artistsSection(stringProvider)
    }

    private fun tracksSection(stringProvider: StringProvider) = tracks.withStandardErrorAndLoading(
        loadingType = LoadingType.TEXT_CAPTION,
        loadingItemCount = TRACKS_LOADING_COUNT,
        loadingWithHeader = true,
        loadingOperationNameOverride = SECTION_NAME_TRACKS,
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

    private fun gamesSection(stringProvider: StringProvider) = games.withStandardErrorAndLoading(
        loadingType = LoadingType.COVER,
        loadingWithHeader = true,
        loadingOperationNameOverride = SECTION_NAME_GAMES,
    ) {
        if (data.isEmpty()) {
            emptyList()
        } else {
            listOf(
                SectionHeaderListModel(
                    stringProvider.getString(ChipboxStringId.FAVORITES_SECTION_GAMES),
                ),
            ) + data.map { game ->
                GridImageListModel(
                    dataId = game.id + ID_PREFIX_GAMES,
                    name = game.title,
                    sourceInfo = game.photoUrl,
                    imagePlaceholder = Icon.Album,
                    clickAction = FavoritesAction.GameClicked(game.id),
                    aspectRatio = GAME_COVER_ASPECT_RATIO,
                )
            }
        }
    }

    private fun artistsSection(stringProvider: StringProvider) = artists.withStandardErrorAndLoading(
        loadingType = LoadingType.SQUARE,
        loadingWithHeader = true,
        loadingOperationNameOverride = SECTION_NAME_ARTISTS,
    ) {
        if (data.isEmpty()) {
            emptyList()
        } else {
            listOf(
                SectionHeaderListModel(
                    stringProvider.getString(ChipboxStringId.FAVORITES_SECTION_ARTISTS),
                ),
            ) + data.map { artist ->
                GridImageListModel(
                    dataId = artist.id + ID_PREFIX_ARTISTS,
                    name = artist.name,
                    sourceInfo = artist.photoUrl,
                    imagePlaceholder = Icon.Person,
                    clickAction = FavoritesAction.ArtistClicked(artist.id),
                )
            }
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

        private const val GRID_CELL_WIDTH_DP = 160
        private const val TRACKS_LOADING_COUNT = 8

        // 3:4 — IGDB serves covers at 528×704. Kept literal because the feature layer doesn't
        // depend on the cbox UI module where CoverArtConstants lives.
        private const val GAME_COVER_ASPECT_RATIO = 0.75f

        private const val MILLIS_PER_SECOND = 1_000L
        private const val SECONDS_PER_MINUTE = 60L

        private const val ID_PREFIX_TRACKS = 1_000_000L
        private const val ID_PREFIX_GAMES = 1_000_000_000L
        private const val ID_PREFIX_ARTISTS = 2_000_000_000L
    }
}
