package net.sigmabeta.chipbox.features.playlistdetail

import net.sigmabeta.chipbox.common.ui.components.api.DraggableListModel
import net.sigmabeta.chipbox.models.Playlist
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.IconNameCaptionListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.NameCaptionValueListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

/**
 * The number of fixed (non-draggable) CTA rows the edit-mode list shows *above* the track rows
 * (Done, Rename, Delete). The reducer subtracts this when turning a [SageAction.Reorder]'s list
 * indices into track positions — kept here so the layout and the reducer can't drift.
 */
internal const val PLAYLIST_EDIT_HEADER_ROWS = 3

data class PlaylistDetailState(
    val playlist: LCE<Playlist> = LCE.Uninitialized,
    val tracks: LCE<List<Track>> = LCE.Uninitialized,
    val notFound: Boolean = false,
    // Edit mode marks the track rows draggable + removable and swaps the view-mode CTA for the
    // manage CTAs. Both modes render through ChipboxReorderableEntry; only the rows differ.
    val isEditing: Boolean = false,
) : ListState() {
    override val columnType: ColumnType = ColumnType.One

    override fun title(stringProvider: StringProvider) = when (playlist) {
        is LCE.Content -> TitleBarModel(title = playlist.data.name)
        else -> TitleBarModel()
    }

    override fun toListItems(stringProvider: StringProvider): List<ListModel> = when {
        notFound -> listOf(
            EmptyStateListModel(
                icon = Icon.QueueMusic,
                explanation = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_NOT_FOUND),
            ),
        )

        isEditing -> editHeader(stringProvider) + tracksSection(stringProvider, editing = true)

        else -> viewHeader(stringProvider) + tracksSection(stringProvider, editing = false)
    }

    // View mode: a single "Edit playlist" CTA, shown once the playlist itself has loaded.
    private fun viewHeader(stringProvider: StringProvider): List<ListModel> =
        if (playlist is LCE.Content) {
            listOf(
                CtaListModel(
                    icon = Icon.Edit,
                    name = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_CTA_EDIT),
                    clickAction = PlaylistDetailAction.EditClicked,
                ),
            )
        } else {
            emptyList()
        }

    // Edit mode: Done / Rename / Delete. MUST be exactly [PLAYLIST_EDIT_HEADER_ROWS] rows (the
    // reducer relies on that count to map reorder indices to track positions).
    private fun editHeader(stringProvider: StringProvider): List<ListModel> = listOf(
        CtaListModel(
            icon = Icon.Save,
            name = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_CTA_DONE),
            clickAction = PlaylistDetailAction.DoneClicked,
        ),
        CtaListModel(
            icon = Icon.Edit,
            name = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_CTA_RENAME),
            clickAction = PlaylistDetailAction.RenameClicked,
        ),
        CtaListModel(
            icon = Icon.Delete,
            name = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_CTA_DELETE),
            clickAction = PlaylistDetailAction.DeleteClicked,
        ),
    )

    private fun tracksSection(stringProvider: StringProvider, editing: Boolean): List<ListModel> =
        tracks.withStandardErrorAndLoading(
            loadingType = LoadingType.TEXT_CAPTION,
            loadingItemCount = TRACKS_LOADING_COUNT,
            loadingWithHeader = false,
        ) {
            if (data.isEmpty()) {
                listOf(
                    EmptyStateListModel(
                        icon = Icon.QueueMusic,
                        explanation = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_EMPTY),
                        showCrossOut = false,
                    ),
                )
            } else if (editing) {
                data.map(::editTrackRow)
            } else {
                data.map(::viewTrackRow)
            }
        }

    // Playback from a playlist is out of scope for now, so view-mode rows don't act on tap (Noop).
    private fun viewTrackRow(track: Track): ListModel = NameCaptionValueListModel(
        dataId = track.id,
        name = track.title,
        caption = track.game?.title.orEmpty(),
        value = formatTrackLength(track.trackLengthMs),
        clickAction = SageAction.Noop,
    )

    // Edit-mode row: wrapped in [DraggableListModel] for the reorder handle; a leading minus icon
    // and a row tap remove the track.
    private fun editTrackRow(track: Track): ListModel = DraggableListModel(
        IconNameCaptionListModel(
            dataId = track.id,
            name = track.title,
            caption = track.game?.title.orEmpty(),
            icon = Icon.Minus,
            clickAction = PlaylistDetailAction.TrackRemoved(track.id),
        ),
    )

    private fun formatTrackLength(millis: Long): String {
        val totalSeconds = millis / MILLIS_PER_SECOND
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    private companion object {
        const val TRACKS_LOADING_COUNT = 8

        const val MILLIS_PER_SECOND = 1_000L
        const val SECONDS_PER_MINUTE = 60L
    }
}
