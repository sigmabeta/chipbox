package net.sigmabeta.chipbox.features.browsealltracks

import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.NameCaptionValueListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class BrowseAllTracksState(
    val tracks: LCE<List<Track>> = LCE.Uninitialized,
    val playingTrackId: Long? = null,
) : ListState() {
    override val columnType: ColumnType = ColumnType.One

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.LIBRARY_BROWSE_ALL_TRACKS),
    )

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = tracks.withStandardErrorAndLoading(
            loadingType = LoadingType.TEXT_CAPTION,
            loadingItemCount = LOADING_COUNT,
            loadingWithHeader = false,
        ) { content(data, stringProvider) }

    private fun content(tracks: List<Track>, stringProvider: StringProvider): List<ListModel> = if (tracks.isEmpty()) {
            listOf(
                EmptyStateListModel(
                    icon = Icon.MusicNote,
                    explanation = stringProvider.getString(ChipboxStringId.LIBRARY_BROWSE_ALL_TRACKS_EMPTY),
                )
            )
        } else {
            listOf(
                CtaListModel(
                    icon = Icon.Shuffle,
                    name = stringProvider.getString(ChipboxStringId.LIBRARY_BROWSE_ALL_TRACKS_CTA_SHUFFLE_ALL),
                    clickAction = BrowseAllTracksAction.ShuffleAllClicked,
                ),
            ) + tracks.mapIndexed(::trackRow)
        }

    private fun trackRow(index: Int, track: Track): ListModel = NameCaptionValueListModel(
            dataId = track.id,
            name = track.title,
            caption = track.game?.title.orEmpty(),
            value = formatTrackLength(track.trackLengthMs),
            clickAction = BrowseAllTracksAction.TrackClicked(index),
            active = track.id == playingTrackId,
        )

    private fun formatTrackLength(millis: Long): String {
        val totalSeconds = millis / MILLIS_PER_SECOND
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return "%d:%02d".format(minutes, seconds)
    }

    private companion object {
        const val LOADING_COUNT = 12

        const val MILLIS_PER_SECOND = 1_000L
        const val SECONDS_PER_MINUTE = 60L
    }
}
