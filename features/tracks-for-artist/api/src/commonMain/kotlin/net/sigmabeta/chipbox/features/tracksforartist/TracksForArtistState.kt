package net.sigmabeta.chipbox.features.tracksforartist

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

/**
 * A flat, lazily-scrolling list of every track by one artist. This is the "View all songs"
 * destination for a prolific composer — the artist-detail screen renders only a bounded preview
 * (see `ArtistDetailState.SONGS_PREVIEW_COUNT`) because its section renders as one non-lazy
 * column. Here the list is a plain [ColumnType.One] `ListScreen`, so the full list can be
 * arbitrary length without freezing the main thread.
 */
data class TracksForArtistState(
    val artistName: String = "",
    val tracks: LCE<List<Track>> = LCE.Uninitialized,
    val playingTrackId: Long? = null,
) : ListState() {
    override val columnType: ColumnType = ColumnType.One

    override fun title(stringProvider: StringProvider) = if (artistName.isNotEmpty()) {
        TitleBarModel(title = artistName)
    } else {
        TitleBarModel()
    }

    override fun toListItems(stringProvider: StringProvider): List<ListModel> {
        // The play/shuffle CTAs aren't gated on the track data finishing loading — show them up
        // front (during loading too) so the user can act immediately. Hidden only when there is
        // nothing to play: a loaded-but-empty artist or a load error.
        val showCtas = when (val lce = tracks) {
            is LCE.Content -> lce.data.isNotEmpty()
            is LCE.Loading -> true
            LCE.Uninitialized -> true
            is LCE.Error -> false
        }
        val ctas = if (showCtas) listOf(playAllCta(stringProvider), shuffleAllCta(stringProvider)) else emptyList()
        val content = tracks.withStandardErrorAndLoading(
            loadingType = LoadingType.TEXT_CAPTION,
            loadingItemCount = LOADING_COUNT,
            loadingWithHeader = false,
        ) { content(data, stringProvider) }
        return ctas + content
    }

    private fun playAllCta(stringProvider: StringProvider) = CtaListModel(
        icon = Icon.MusicNote,
        name = stringProvider.getString(ChipboxStringId.ARTIST_DETAIL_CTA_PLAY_ALL),
        clickAction = TracksForArtistAction.PlayAllClicked,
    )

    private fun shuffleAllCta(stringProvider: StringProvider) = CtaListModel(
        icon = Icon.Shuffle,
        name = stringProvider.getString(ChipboxStringId.ARTIST_DETAIL_CTA_SHUFFLE_ALL),
        clickAction = TracksForArtistAction.ShuffleAllClicked,
    )

    private fun content(tracks: List<Track>, stringProvider: StringProvider): List<ListModel> =
        if (tracks.isEmpty()) {
            listOf(
                EmptyStateListModel(
                    icon = Icon.MusicNote,
                    explanation = stringProvider.getString(ChipboxStringId.TRACKS_FOR_ARTIST_EMPTY),
                )
            )
        } else {
            tracks.mapIndexed(::trackRow)
        }

    // Click position is the index within the title/game-ordered list, matching the order the
    // Director resolves for an ARTIST session — so playback starts on the tapped track.
    private fun trackRow(index: Int, track: Track): ListModel = NameCaptionValueListModel(
        dataId = track.id,
        name = track.title,
        caption = track.game?.title.orEmpty(),
        value = formatTrackLength(track.trackLengthMs),
        clickAction = TracksForArtistAction.TrackClicked(index),
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

        const val MILLIS_PER_SECOND = 1_000L
        const val SECONDS_PER_MINUTE = 60L
    }
}
