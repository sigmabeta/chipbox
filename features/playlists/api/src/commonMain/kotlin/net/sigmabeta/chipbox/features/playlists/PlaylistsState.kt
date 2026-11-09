package net.sigmabeta.chipbox.features.playlists

import net.sigmabeta.chipbox.models.Playlist
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.appcomm.LCE
import net.sigmabeta.sage.components.CtaListModel
import net.sigmabeta.sage.components.EmptyStateListModel
import net.sigmabeta.sage.components.IconNameCaptionListModel
import net.sigmabeta.sage.components.ListModel
import net.sigmabeta.sage.components.LoadingType
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.list.ColumnType
import net.sigmabeta.sage.list.ListState
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.StringProvider

data class PlaylistsState(
    val playlists: LCE<List<Playlist>> = LCE.Content(emptyList()),
    // In picker mode the screen is an "Add to Playlist" target chooser; only the title differs.
    val isPicker: Boolean = false,
) : ListState() {
    override val columnType: ColumnType = ColumnType.One

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(
            if (isPicker) ChipboxStringId.PLAYLISTS_ADD_TITLE else ChipboxStringId.PLAYLISTS_TITLE,
        ),
    )

    // The "New Playlist" CTA is always present (even while loading or empty) — it's the primary
    // action and the only way to create a playlist from this screen.
    override fun toListItems(stringProvider: StringProvider): List<ListModel> =
        listOf(newPlaylistCta(stringProvider)) + body(stringProvider)

    private fun newPlaylistCta(stringProvider: StringProvider) = CtaListModel(
        icon = Icon.Plus,
        name = stringProvider.getString(ChipboxStringId.PLAYLISTS_CTA_NEW),
        clickAction = PlaylistsAction.NewPlaylistClicked,
    )

    private fun body(stringProvider: StringProvider): List<ListModel> =
        playlists.withStandardErrorAndLoading(
            loadingType = LoadingType.TEXT_CAPTION,
            loadingItemCount = LOADING_COUNT,
            loadingWithHeader = false,
        ) {
            if (data.isEmpty()) {
                listOf(
                    EmptyStateListModel(
                        icon = Icon.QueueMusic,
                        explanation = stringProvider.getString(ChipboxStringId.PLAYLISTS_EMPTY),
                    ),
                )
            } else {
                data.map { playlist -> playlistRow(playlist, stringProvider) }
            }
        }

    private fun playlistRow(playlist: Playlist, stringProvider: StringProvider) = IconNameCaptionListModel(
        dataId = playlist.id,
        name = playlist.name,
        caption = trackCountCaption(playlist.trackCount, stringProvider),
        icon = Icon.QueueMusic,
        clickAction = PlaylistsAction.PlaylistClicked(playlist.id),
    )

    private fun trackCountCaption(count: Int, stringProvider: StringProvider): String = if (count == 1) {
        stringProvider.getString(ChipboxStringId.PLAYLISTS_TRACK_COUNT_ONE)
    } else {
        stringProvider.getStringOneInt(ChipboxStringId.PLAYLISTS_TRACK_COUNT, count)
    }

    private companion object {
        const val LOADING_COUNT = 6
    }
}
