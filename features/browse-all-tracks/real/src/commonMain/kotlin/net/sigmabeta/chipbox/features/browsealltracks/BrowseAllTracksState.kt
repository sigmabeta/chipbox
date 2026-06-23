package net.sigmabeta.chipbox.features.browsealltracks

import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingItemListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.NameCaptionValueListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.list.PaginationType
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class BrowseAllTracksState(
    val tracks: LCE<List<Track>> = LCE.Uninitialized,
    val playingTrackId: Long? = null,
    // Paging window: [windowStart, windowStart + tracks.size) within the title-ordered catalog.
    val windowStart: Int = 0,
    val hasMoreBefore: Boolean = false,
    val hasMoreAfter: Boolean = true,
    val loadingPrevious: Boolean = false,
    val loadingMore: Boolean = false,
) : ListState() {
    override val columnType: ColumnType = ColumnType.One

    override val paginationType: PaginationType = PaginationType.Standard()

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.LIBRARY_BROWSE_ALL_TRACKS),
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> {
        // The shuffle-all CTA isn't gated on the track data finishing loading — show it up
        // front (during loading too) so the user can act immediately. It's hidden only when
        // there's nothing to shuffle: a loaded-but-empty library or a load error.
        val showCta = when (val lce = tracks) {
            is LCE.Content -> lce.data.isNotEmpty()
            is LCE.Loading -> true
            LCE.Uninitialized -> true
            is LCE.Error -> false
        }
        val cta = if (showCta) listOf(shuffleAllCta(stringProvider)) else emptyList()
        val content = tracks.withStandardErrorAndLoading(
            loadingType = LoadingType.TEXT_CAPTION,
            loadingItemCount = LOADING_COUNT,
            loadingWithHeader = false,
        ) { content(data, stringProvider) }
        // Inline header/footer spinners shown while paging a non-empty window further up or down.
        return cta +
            pageLoader(loadingPrevious, LOAD_PREVIOUS_OP) +
            content +
            pageLoader(loadingMore, LOAD_MORE_OP)
    }

    private fun pageLoader(show: Boolean, operationName: String): List<ListModel> {
        if (!show) return emptyList()
        val type = paginationType as? PaginationType.Paginating ?: return emptyList()
        return listOf(
            LoadingItemListModel(
                loadingType = type.loadingType,
                loadOperationName = operationName,
                loadPositionOffset = 0,
            )
        )
    }

    private fun shuffleAllCta(stringProvider: StringProvider) = CtaListModel(
        icon = Icon.Shuffle,
        name = stringProvider.getString(ChipboxStringId.LIBRARY_BROWSE_ALL_TRACKS_CTA_SHUFFLE_ALL),
        clickAction = BrowseAllTracksAction.ShuffleAllClicked,
    )

    private fun content(tracks: List<Track>, stringProvider: StringProvider): List<ListModel> = if (tracks.isEmpty()) {
            listOf(
                EmptyStateListModel(
                    icon = Icon.MusicNote,
                    explanation = stringProvider.getString(ChipboxStringId.LIBRARY_BROWSE_ALL_TRACKS_EMPTY),
                )
            )
        } else {
            tracks.mapIndexed(::trackRow)
        }

    private fun trackRow(index: Int, track: Track): ListModel = NameCaptionValueListModel(
            dataId = track.id,
            name = track.title,
            caption = track.game?.title.orEmpty(),
            value = formatTrackLength(track.trackLengthMs),
            // Absolute position in the title-ordered catalog (window may start past 0), so playback
        // starts on the right track regardless of how far the window has been paged.
        clickAction = BrowseAllTracksAction.TrackClicked(windowStart + index),
            active = track.id == playingTrackId,
        )

    private fun formatTrackLength(millis: Long): String {
        val totalSeconds = millis / MILLIS_PER_SECOND
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    private companion object {
        const val LOADING_COUNT = 12

        const val LOAD_PREVIOUS_OP = "browse_all_tracks.load_previous"
        const val LOAD_MORE_OP = "browse_all_tracks.load_more"

        const val MILLIS_PER_SECOND = 1_000L
        const val SECONDS_PER_MINUTE = 60L
    }
}
