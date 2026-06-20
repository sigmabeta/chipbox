package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import net.sigmabeta.chipbox.common.ui.components.api.IconNameListItem
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.ui.Icon

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
                    icon = Icon.FavoriteEmpty,
                    clickAction = NowPlayingAction.AddToFavoritesClicked,
                    actionSink = actionSink,
                    tag = NOW_PLAYING_CTX_FAVORITES_TAG,
                )
            }

            ContextMenuMode.NONE -> Unit
        }
    }
}

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
