package net.sigmabeta.chipbox.features.playlistdetail

import net.sigmabeta.chipbox.common.ui.components.api.DraggableListModel
import net.sigmabeta.chipbox.models.Playlist
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.ConfirmationListModel
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.EditTextListModel
import net.sigmabeta.sage.components.EmptyStateListModel
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
    // While renaming, the edit-mode "Rename playlist" CTA is replaced in place by an inline
    // edit-text row. Still one header row, so the reorder index mapping is unaffected.
    val isRenaming: Boolean = false,
    // While confirming a delete, the "Delete playlist" CTA is replaced in place by an inline
    // confirmation row. Still one header row, so the reorder index mapping is unaffected.
    val isConfirmingDelete: Boolean = false,
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

    // Edit mode: Done / (Rename CTA or inline rename field) / Delete. MUST be exactly
    // [PLAYLIST_EDIT_HEADER_ROWS] rows (the reducer relies on that count to map reorder indices to
    // track positions) — the rename field replaces the Rename CTA in place, so the count holds.
    private fun editHeader(stringProvider: StringProvider): List<ListModel> = listOf(
        CtaListModel(
            icon = Icon.Save,
            name = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_CTA_DONE),
            clickAction = PlaylistDetailAction.DoneClicked,
        ),
        if (isRenaming) renameField(stringProvider) else renameCta(stringProvider),
        if (isConfirmingDelete) deleteConfirmation(stringProvider) else deleteCta(stringProvider),
    )

    private fun deleteCta(stringProvider: StringProvider): ListModel = CtaListModel(
        icon = Icon.Delete,
        name = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_CTA_DELETE),
        clickAction = PlaylistDetailAction.DeleteClicked,
    )

    // Inline "Delete playlist?" confirmation; confirm/cancel dispatch SageAction.Confirmation*.
    private fun deleteConfirmation(stringProvider: StringProvider): ListModel = ConfirmationListModel(
        id = DELETE_CONFIRM_ID,
        header = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_DELETE_HEADER),
        bodyText = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_DELETE_BODY),
        confirmLabel = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_DELETE_CONFIRM),
        cancelLabel = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_DELETE_CANCEL),
    )

    private fun renameCta(stringProvider: StringProvider): ListModel = CtaListModel(
        icon = Icon.Edit,
        name = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_CTA_RENAME),
        clickAction = PlaylistDetailAction.RenameClicked,
    )

    // The inline rename row. Its submit/cancel dispatch SageAction.EditTextSubmitted/Cancelled; the
    // VM renames (or dismisses) and clears [isRenaming]. Prefilled with the current name — unless
    // that's still the auto-generated default, in which case start blank so the hint shows and the
    // user names it fresh. autoFocus is safe since there's only ever this one edit-text row.
    private fun renameField(stringProvider: StringProvider): ListModel {
        val currentName = (playlist as? LCE.Content)?.data?.name.orEmpty()
        val defaultBase = stringProvider.getString(ChipboxStringId.PLAYLISTS_DEFAULT_NAME)
        return EditTextListModel(
            id = RENAME_EDIT_TEXT_ID,
            header = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_CTA_RENAME),
            hint = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_RENAME_HINT),
            initialText = if (isDefaultName(currentName, defaultBase)) "" else currentName,
            submitLabel = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_RENAME_SUBMIT),
            cancelLabel = stringProvider.getString(ChipboxStringId.PLAYLIST_DETAIL_RENAME_CANCEL),
            allowEmpty = false,
            autoFocus = true,
        )
    }

    // The auto-assigned default is the base name or the base plus a numeric suffix ("New Playlist",
    // "New Playlist 2", …) — matching how new playlists are named — so an untouched name prefills blank.
    private fun isDefaultName(name: String, base: String): Boolean =
        name == base || (name.startsWith("$base ") && name.removePrefix("$base ").toIntOrNull() != null)

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

    // Edit-mode row: wrapped in [DraggableListModel] for the reorder handle, with a dismissAction so
    // a right-to-left swipe removes the track. Same content as the view row (no leading icon, no tap).
    private fun editTrackRow(track: Track): ListModel = DraggableListModel(
        content = NameCaptionValueListModel(
            dataId = track.id,
            name = track.title,
            caption = track.game?.title.orEmpty(),
            value = formatTrackLength(track.trackLengthMs),
            clickAction = SageAction.Noop,
        ),
        dismissAction = PlaylistDetailAction.TrackRemoved(track.id),
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

        // dataId for the inline rename row — a sentinel that can't collide with a track id
        // (positive) or a CtaListModel's name-hash dataId. The VM ignores the action's id (it
        // already knows the playlist), so the exact value only needs to be list-unique.
        const val RENAME_EDIT_TEXT_ID = Long.MIN_VALUE

        // Same idea for the inline delete-confirmation row; distinct from [RENAME_EDIT_TEXT_ID].
        const val DELETE_CONFIRM_ID = Long.MIN_VALUE + 1
    }
}
