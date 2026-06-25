package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.IconNameListItem
import net.sigmabeta.chipbox.common.ui.components.api.utils.ImageSize
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.ui.Icon
import androidx.compose.material3.Icon as Material3Icon
import net.sigmabeta.sage.ui.vector

/**
 * The in-screen "ContextMenu" that replaces [TrackInfo] while a [ContextMenuMode] other than NONE
 * is active. A surface-tinted, rounded card of clickable rows: a back row (the track name) that
 * returns to NONE, followed by mode-specific rows (game/artist links, an artist picker, or the
 * repeat/shuffle controls). Every row dispatches an action; the VM owns the resulting state and the
 * auto-dismiss timer.
 */
@Composable
internal fun ContextMenu(model: NowPlayingModel, mode: ContextMenuMode, actionSink: ActionSink) {
    // Keeps its own 200..600dp width; the rounded surfaceContainer background lives on the shared
    // container now.
    Column(
        modifier = Modifier.widthIn(min = NowPlayingPanelMinWidth, max = NowPlayingPanelMaxWidth),
    ) {
        ContextMenuRow(
            name = model.title,
            icon = Icon.Back,
            clickAction = NowPlayingAction.ContextMenuBackClicked,
            actionSink = actionSink,
            tag = NOW_PLAYING_CTX_BACK_TAG,
        )

        when (mode) {
            ContextMenuMode.LINKS -> {
                if (model.gameTitle.isNotEmpty()) {
                    ContextMenuRow(
                        name = model.gameTitle,
                        icon = Icon.Album,
                        clickAction = NowPlayingAction.ContextMenuGameClicked,
                        actionSink = actionSink,
                        tag = NOW_PLAYING_CTX_GAME_TAG,
                    )
                }
                if (model.artists.isNotEmpty()) {
                    ContextMenuRow(
                        name = model.artistsCaption,
                        icon = Icon.Person,
                        clickAction = NowPlayingAction.ContextMenuArtistsClicked,
                        actionSink = actionSink,
                        tag = NOW_PLAYING_CTX_ARTISTS_TAG,
                    )
                }
                if (model.platformLabel.isNotEmpty()) {
                    ContextMenuRow(
                        name = model.platformLabel,
                        icon = Icon.Chip,
                        clickAction = NowPlayingAction.ContextMenuPlatformClicked,
                        actionSink = actionSink,
                        tag = NOW_PLAYING_CTX_PLATFORM_TAG,
                    )
                }
                // Descriptive metadata from the track's file tags. LINKS is the track's "about"
                // surface; a tag long enough to be ellipsized here opens the TAG view on tap.
                MetadataRow(NowPlayingTagKind.JAPANESE_TITLE, model.japaneseTitle, NOW_PLAYING_CTX_JP_TITLE_TAG, actionSink)
                MetadataRow(NowPlayingTagKind.JAPANESE_ARTIST, model.japaneseArtist, NOW_PLAYING_CTX_JP_ARTIST_TAG, actionSink)
                MetadataRow(NowPlayingTagKind.DUMPER, model.dumper, NOW_PLAYING_CTX_DUMPER_TAG, actionSink)
                MetadataRow(NowPlayingTagKind.DUMP_DATE, model.dumpDate, NOW_PLAYING_CTX_DUMP_DATE_TAG, actionSink)
                MetadataRow(NowPlayingTagKind.COMMENT, model.comment, NOW_PLAYING_CTX_COMMENT_TAG, actionSink)
            }

            ContextMenuMode.ARTISTS -> {
                model.artists.forEach { artist ->
                    ContextMenuRow(
                        name = artist.name,
                        icon = Icon.Person,
                        clickAction = NowPlayingAction.ContextMenuArtistClicked(artist.id),
                        actionSink = actionSink,
                        tag = nowPlayingCtxArtistTag(artist.id),
                    )
                }
            }

            ContextMenuMode.CONTROLS -> {
                ContextMenuRow(
                    name = model.repeatStatusLabel,
                    icon = if (model.repeatMode == RepeatMode.ONE) Icon.RepeatOne else Icon.Repeat,
                    clickAction = NowPlayingAction.RepeatClicked,
                    actionSink = actionSink,
                    tag = NOW_PLAYING_CTX_REPEAT_TAG,
                    active = model.repeatMode != RepeatMode.OFF,
                )
                ContextMenuRow(
                    name = model.shuffleStatusLabel,
                    icon = Icon.Shuffle,
                    clickAction = NowPlayingAction.ShuffleClicked,
                    actionSink = actionSink,
                    tag = NOW_PLAYING_CTX_SHUFFLE_TAG,
                    active = model.isShuffled,
                )
                ContextMenuRow(
                    name = model.favoriteLabel,
                    icon = if (model.isTrackFavorite) Icon.FavoriteFilled else Icon.FavoriteEmpty,
                    clickAction = NowPlayingAction.AddToFavoritesClicked,
                    actionSink = actionSink,
                    tag = NOW_PLAYING_CTX_FAVORITES_TAG,
                    active = model.isTrackFavorite,
                )
                ContextMenuRow(
                    name = model.addToPlaylistLabel,
                    icon = Icon.QueueMusic,
                    clickAction = NowPlayingAction.AddToPlaylistClicked,
                    actionSink = actionSink,
                    tag = NOW_PLAYING_CTX_ADD_PLAYLIST_TAG,
                )
            }

            ContextMenuMode.TAG -> model.selectedTag?.let { TagContent(it) }

            ContextMenuMode.NONE -> Unit
        }
    }
}

/**
 * A LINKS row for one optional file-tag metadata value. Renders nothing when [value] is blank.
 * A value too long to read on the single ellipsized line ([isExpandableTag]) is tappable and opens
 * the [ContextMenuMode.TAG] view; a short one already fits, so its tap is inert ([SageAction.Noop]).
 */
@Composable
private fun MetadataRow(kind: NowPlayingTagKind, value: String, tag: String, actionSink: ActionSink) {
    if (value.isEmpty()) return
    val clickAction: SageAction =
        if (isExpandableTag(value)) NowPlayingAction.ContextMenuTagClicked(kind) else SageAction.Noop
    IconNameListItem(
        name = value,
        icon = iconFor(kind),
        clickAction = clickAction,
        active = false,
        actionSink = actionSink,
        modifier = Modifier.testTag(tag),
        padding = NowPlayingRowPadding,
    )
}

/**
 * The full text of one metadata tag — the TAG view's body, shown below the shared back row. Wraps
 * across as many lines as it needs and scrolls within [TagMaxHeight] so a long comment is fully
 * readable without unbounding the panel.
 */
@Composable
private fun TagContent(tag: NowPlayingTag) {
    Row(
        modifier = Modifier
            .testTag(NOW_PLAYING_CTX_TAG_TAG)
            .heightIn(max = TagMaxHeight)
            .verticalScroll(rememberScrollState())
            .padding(NowPlayingRowPadding)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Material3Icon(
            imageVector = iconFor(tag.kind).vector(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .size(ImageSize.THUMBNAIL.size)
                .padding(end = 8.dp),
        )
        Text(
            text = tag.value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The icon a metadata tag shows in both its LINKS row and the TAG view. */
private fun iconFor(kind: NowPlayingTagKind): Icon = when (kind) {
    NowPlayingTagKind.JAPANESE_TITLE -> Icon.MusicNote
    NowPlayingTagKind.JAPANESE_ARTIST -> Icon.Person
    NowPlayingTagKind.DUMPER -> Icon.Edit
    NowPlayingTagKind.DUMP_DATE -> Icon.Calendar
    NowPlayingTagKind.COMMENT -> Icon.Description
}

/**
 * Whether a tag's value is too long to read on the single ellipsized LINKS row, and so should be
 * tappable to open the TAG view. A cheap content heuristic (multi-line, or longer than
 * [TAG_INLINE_MAX_CHARS]) rather than measuring actual text overflow.
 */
internal fun isExpandableTag(value: String): Boolean =
    value.contains('\n') || value.length > TAG_INLINE_MAX_CHARS

/** Tag values at or below this length are assumed to fit the single LINKS row. */
private const val TAG_INLINE_MAX_CHARS = 20

/** Max height of the scrollable TAG body before it scrolls instead of growing the panel. */
private val TagMaxHeight = 400.dp

@Composable
private fun ContextMenuRow(
    name: String,
    icon: Icon,
    clickAction: NowPlayingAction,
    actionSink: ActionSink,
    tag: String,
    active: Boolean = false,
) {
    IconNameListItem(
        name = name,
        icon = icon,
        clickAction = clickAction,
        active = active,
        actionSink = actionSink,
        modifier = Modifier.testTag(tag),
        padding = NowPlayingRowPadding,
    )
}
