package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.DraggableListItem
import net.sigmabeta.chipbox.common.ui.components.api.IconNameListItem
import net.sigmabeta.chipbox.common.ui.components.api.NameCaptionValueListItem
import net.sigmabeta.chipbox.common.ui.components.api.previews.CoverArtConstants
import net.sigmabeta.chipbox.common.ui.components.api.subs.CrossfadeImage
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.NameCaptionValueListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.vector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val ScreenPadding = 24.dp
private val ArtworkCornerRadius = 16.dp

// At/under this width the middle swaps between track info and the setlist; above it there's room
// to show both side by side.
private val WideLayoutBreakpoint = 780.dp
private val TransportPlayPauseSize = 80.dp
private val TransportSkipSize = 64.dp
private val TransportToggleSize = 48.dp

/**
 * Test tags for the track-info block, the two text-less transport buttons, and the context-menu
 * rows, so UI tests can target them via the semantics click action — which (unlike an injected
 * gesture) isn't defeated by the bottom mini-player overlapping a control, and (unlike matching by
 * text) doesn't collide with the same track title/artist the mini-player shows. Kept in sync with
 * the literals in `NowPlayingTest`.
 */
const val NOW_PLAYING_TRACK_INFO_TAG = "NowPlayingTrackInfo"
const val NOW_PLAYING_MENU_BUTTON_TAG = "NowPlayingMenuButton"
const val NOW_PLAYING_SETLIST_BUTTON_TAG = "NowPlayingSetlistButton"
const val NOW_PLAYING_CTX_BACK_TAG = "NowPlayingCtxBack"
const val NOW_PLAYING_CTX_GAME_TAG = "NowPlayingCtxGame"
const val NOW_PLAYING_CTX_ARTISTS_TAG = "NowPlayingCtxArtists"
const val NOW_PLAYING_CTX_REPEAT_TAG = "NowPlayingCtxRepeat"
const val NOW_PLAYING_CTX_SHUFFLE_TAG = "NowPlayingCtxShuffle"

/** Test tag for the per-artist row in the ARTISTS context menu, keyed by artist id. */
fun nowPlayingCtxArtistTag(artistId: Long) = "NowPlayingCtxArtist:$artistId"

// TrackInfo and the ContextMenu share one container (rounded shape + the animated background), but
// keep their own width bounds: TrackInfo wraps its content down to a 256dp floor (no cap); the menu
// keeps its 200..600dp range.
private val TrackInfoMinWidth = 256.dp
private val ContextMenuMinWidth = 200.dp
private val ContextMenuMaxWidth = 600.dp
private val InfoContainerCornerRadius = 16.dp
private val TrackInfoInteriorPadding = 8.dp
private val ContextMenuRowPadding = PaddingValues(horizontal = 8.dp)

// Under this height there's no room for the tall cover-art + text layout, so the track info
// collapses to the compact home-card-style MiniNowPlayingTrackInfo.
private val CompactHeightBreakpoint = 500.dp
private const val MINI_CARD_SCRIM_ALPHA = 0.45f

@Composable
fun NowPlayingContent(
    model: NowPlayingModel,
    actionSink: ActionSink,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Above the breakpoint there's room to show the track info and the setlist together.
        val wide = maxWidth > WideLayoutBreakpoint
        // Under the height breakpoint the track info collapses to the compact mini card.
        val compact = maxHeight < CompactHeightBreakpoint

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(model, actionSink)

            Spacer(modifier = Modifier.height(16.dp))

            // Crossfade the whole middle when crossing the breakpoint so the layout doesn't snap.
            AnimatedContent(
                targetState = wide,
                label = "NowPlayingLayout",
                contentAlignment = Alignment.Center,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { isWide ->
                if (isWide) {
                    // Two panels: the info panel on the left, the setlist always on the right.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        InfoPanel(model, actionSink, compact, modifier = Modifier.weight(1f).fillMaxHeight())
                        NowPlayingSetlist(model, actionSink, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    // One panel: the setlist button swaps the info panel out for the setlist; the
                    // header and transport controls stay put.
                    AnimatedContent(
                        targetState = model.setlistVisible,
                        label = "NowPlayingMiddle",
                        contentAlignment = Alignment.Center,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier.fillMaxSize(),
                    ) { showingSetlist ->
                        if (showingSetlist) {
                            NowPlayingSetlist(model, actionSink, Modifier.fillMaxSize())
                        } else {
                            InfoPanel(model, actionSink, compact, Modifier.fillMaxSize())
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ProgressSection(model = model, actionSink = actionSink)

            Spacer(modifier = Modifier.height(16.dp))

            TransportRow(model = model, actionSink = actionSink)
        }
    }
}

/**
 * The primary panel's info mode: the full [NowPlayingInfo], or the compact [MiniNowPlayingInfo]
 * when the screen is too short for the tall cover-art layout.
 */
@Composable
private fun InfoPanel(model: NowPlayingModel, actionSink: ActionSink, compact: Boolean, modifier: Modifier) {
    // Crossfade between the full and compact info layouts when the height crosses the breakpoint.
    AnimatedContent(
        targetState = compact,
        label = "NowPlayingInfoPanel",
        contentAlignment = Alignment.Center,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier,
    ) { isCompact ->
        if (isCompact) {
            // The mini card sits centered in the panel region.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MiniNowPlayingInfo(model, actionSink)
            }
        } else {
            NowPlayingInfo(model, actionSink, Modifier.fillMaxSize())
        }
    }
}

/** The "now playing" panel: cover art, the error log, and the track-info / context-menu block. */
@Composable
private fun NowPlayingInfo(model: NowPlayingModel, actionSink: ActionSink, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Artwork(model)

        ErrorSection(errors = model.errors, actionSink = actionSink)

        // Half the former 16dp gap above the info block lives inside TrackInfo's tap target
        // (TrackInfoInteriorPadding); the other half is this exterior gap.
        Spacer(modifier = Modifier.height(8.dp))

        InfoContainer(model, actionSink)
    }
}

@Composable
private fun ColumnScope.TopBar(model: NowPlayingModel, actionSink: ActionSink) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.BackClicked) },
        ) {
            Icon(
                imageVector = Icon.Caret.vector(),
                contentDescription = null,
            )
        }

        Column(
            modifier = Modifier.weight(1.0f)
        ) {
            if (model.sessionTypeLabel.isNotEmpty()) {
                Text(
                    text = model.sessionTypeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (model.sessionSourceName.isNotEmpty()) {
                    Text(
                        text = model.sessionSourceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.PlayerSettingsClicked) },
        ) {
            Icon(
                imageVector = Icon.Overflow.vector(),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun ColumnScope.Artwork(model: NowPlayingModel) {
    // IGDB covers are 3:4 portrait. Take the available vertical space, then center a 3:4
    // cover sized off that height so the whole cover shows instead of being cropped.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(ArtworkCornerRadius),
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(CoverArtConstants.ASPECT_RATIO)
                .clip(RoundedCornerShape(ArtworkCornerRadius)),
        ) {
            CrossfadeImage(
                sourceInfo = model.artwork,
                imagePlaceholder = Icon.MusicNote,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The container shared by [TrackInfo] and the [ContextMenu]: it wraps whichever is showing (between
 * [InfoContainerMinWidth] and [InfoContainerMaxWidth]) and its background animates from transparent
 * (plain track info) to `surfaceContainer` (any open menu). `animateContentSize` morphs the size as
 * the content swaps, so the `weight(1f)` artwork above reflows smoothly instead of jumping.
 */
@Composable
private fun InfoContainer(model: NowPlayingModel, actionSink: ActionSink) {
    val backgroundColor by animateColorAsState(
        targetValue = if (model.contextMenuMode == ContextMenuMode.NONE) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        label = "NowPlayingInfoBackground",
    )

    // AnimatedContent (not Crossfade) so the content stays centered while the container resizes
    // between the track info and the larger menu: Crossfade's box pins its layers to the top-left,
    // which left the returning TrackInfo stranded in the corner until the resize finished. The
    // built-in SizeTransform animates the size, so the weight(1f) artwork above still reflows.
    AnimatedContent(
        targetState = model.contextMenuMode,
        label = "NowPlayingInfo",
        contentAlignment = Alignment.Center,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = Modifier
            .clip(RoundedCornerShape(InfoContainerCornerRadius))
            .background(backgroundColor),
    ) { mode ->
        // `mode` is the animating layer's own target, not necessarily model.contextMenuMode, so
        // render off it — the outgoing layer keeps showing its old state while it fades.
        if (mode == ContextMenuMode.NONE) {
            TrackInfo(model, actionSink)
        } else {
            ContextMenu(model, mode, actionSink)
        }
    }
}

/**
 * EXPERIMENT: the current play queue rendered in place of the whole [InfoContainer] — the setlist
 * button toggles this swap at the column level, a peer of InfoContainer (which keeps its own
 * TrackInfo↔ContextMenu swap internally). A bounded, scrollable, drag-to-reorder panel reusing
 * [DraggableListItem] (the handle) and [NameCaptionValueListItem] (the row) — the same pieces the
 * standalone setlist screen uses.
 *
 * The drag orchestration here is a compact copy of sage's `ReorderableScreen` (which is coupled to
 * the full-screen list scaffold). Productionizing this would extract a shared reorderable-column
 * composable both can call. On drop it emits [SageAction.Reorder]; the VM owns the canonical order.
 */
@Composable
private fun NowPlayingSetlist(model: NowPlayingModel, actionSink: ActionSink, modifier: Modifier = Modifier) {
    // Local mirror mutated live during a drag; rebuilt whenever the VM re-emits the queue order
    // (data-class row equality means routine playback ticks don't churn it). Mirrors ReorderableScreen.
    val items = remember(model.setlist) { model.setlist.toMutableStateList() }
    var dragStart by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        items.add(to.index, items.removeAt(from.index))
    }
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        modifier = modifier
            .widthIn(min = ContextMenuMinWidth, max = ContextMenuMaxWidth)
            .clip(RoundedCornerShape(InfoContainerCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        itemsIndexed(items, key = { _, row -> row.dataId }) { index, row ->
            ReorderableItem(reorderState, key = row.dataId) { _ ->
                val dragHandle = Modifier.draggableHandle(
                    onDragStarted = {
                        dragStart = row.dataId to index
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragStopped = {
                        val start = dragStart
                        dragStart = null
                        if (start != null) {
                            val finalIndex = items.indexOfFirst { it.dataId == start.first }
                            if (finalIndex >= 0 && finalIndex != start.second) {
                                actionSink.sendAction(SageAction.Reorder(start.second, finalIndex))
                            }
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )

                if (row.active) {
                    // The playing track can be reordered but not removed.
                    SetlistRow(row, dragHandle, actionSink, opaque = false)
                } else {
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            val removed = value == SwipeToDismissBoxValue.EndToStart
                            if (removed) {
                                actionSink.sendAction(NowPlayingAction.SetlistTrackRemoved(row.dataId))
                            }
                            removed
                        },
                    )
                    // True only while the row is actually being swiped left. Drives both the remove
                    // indicator behind the row and the row's own opaque background that masks it.
                    val swiping = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true,
                        backgroundContent = {
                            if (swiping) SwipeRemoveBackground()
                        },
                    ) {
                        SetlistRow(row, dragHandle, actionSink, opaque = swiping)
                    }
                }
            }
        }
    }
}

/**
 * A single setlist row: the track info plus the reorder handle ([dragHandle]). When [opaque] is
 * set it paints a [surfaceContainer][androidx.compose.material3.ColorScheme.surfaceContainer]
 * background — used during a swipe so the remove reveal shows only in the gap the sliding row opens,
 * not bleeding through the row. At rest the row is transparent (the list card shows through).
 */
@Composable
private fun SetlistRow(
    row: NameCaptionValueListModel,
    dragHandle: Modifier,
    actionSink: ActionSink,
    opaque: Boolean,
) {
    DraggableListItem(
        dragHandle = dragHandle,
        modifier = if (opaque) {
            Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
        } else {
            Modifier
        },
    ) {
        NameCaptionValueListItem(
            model = row,
            actionSink = actionSink,
            modifier = Modifier,
            padding = ContextMenuRowPadding,
        )
    }
}

/** The reveal behind a setlist row being swiped away: an error-tinted panel with a remove icon. */
@Composable
private fun SwipeRemoveBackground() {
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
    ) {
        Icon(
            imageVector = Icon.Clear.vector(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun TrackInfo(model: NowPlayingModel, actionSink: ActionSink) {
    // The whole block — interior padding included — is one tap target that opens the LINKS menu.
    // Wraps its content (down to TrackInfoMinWidth) rather than filling; the shared container's
    // AnimatedContent keeps it centered while the container resizes.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = TrackInfoMinWidth)
            .testTag(NOW_PLAYING_TRACK_INFO_TAG)
            .clickable { actionSink.sendAction(NowPlayingAction.TrackInfoClicked) }
            .padding(TrackInfoInteriorPadding),
    ) {
        Text(
            text = model.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (model.artistsCaption.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = model.artistsCaption,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (model.gameTitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = model.gameTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Compact info panel shown in place of [NowPlayingInfo] on short screens, mirroring the Home
 * now-playing card ([net.sigmabeta.chipbox.common.ui.components.api.NowPlayingHomeCard]) — layered
 * artwork → scrim → title/artist text — but without the transport controls. Tapping it opens the
 * LINKS menu, like [TrackInfo], and it carries the same test tag. The fixed-height card is centered
 * in the panel region.
 */
@Composable
private fun MiniNowPlayingInfo(model: NowPlayingModel, actionSink: ActionSink) {
    var imageLoaded by remember(model.artwork.info) { mutableStateOf(false) }
    val foregroundColor by animateColorAsState(
        targetValue = if (imageLoaded) Color.White else MaterialTheme.colorScheme.onSurface,
        label = "MiniNowPlayingInfo.foreground",
    )

    Surface(
        shape = RoundedCornerShape(InfoContainerCornerRadius),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(NOW_PLAYING_TRACK_INFO_TAG)
            .clickable { actionSink.sendAction(NowPlayingAction.TrackInfoClicked) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CrossfadeImage(
                sourceInfo = model.artwork,
                imagePlaceholder = Icon.MusicNote,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                onImageLoadedChange = { imageLoaded = it },
            )
            // Scrim only once a real image is behind the text, for legibility.
            if (imageLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = MINI_CARD_SCRIM_ALPHA)),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = foregroundColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (model.artistsCaption.isNotEmpty()) {
                    Text(
                        text = model.artistsCaption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = foregroundColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The in-screen "ContextMenu" that replaces [TrackInfo] while a [ContextMenuMode] other than NONE
 * is active. A surface-tinted, rounded card of clickable rows: a back row (the track name) that
 * returns to NONE, followed by mode-specific rows (game/artist links, an artist picker, or the
 * repeat/shuffle controls). Every row dispatches an action; the VM owns the resulting state and the
 * auto-dismiss timer.
 */
@Composable
private fun ContextMenu(model: NowPlayingModel, mode: ContextMenuMode, actionSink: ActionSink) {
    // Keeps its own 200..600dp width; the rounded surfaceContainer background lives on the shared
    // container now.
    Column(
        modifier = Modifier.widthIn(min = ContextMenuMinWidth, max = ContextMenuMaxWidth),
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
        padding = ContextMenuRowPadding,
    )
}

@Composable
private fun ColumnScope.ProgressSection(
    model: NowPlayingModel,
    actionSink: ActionSink,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val maxValue = model.lengthMs.coerceAtLeast(1L).toFloat()
    val displayValue = (dragValue ?: model.positionMs.toFloat()).coerceIn(0f, maxValue)
    val cacheFraction = (model.cachedMs.toFloat() / maxValue).coerceIn(0f, 1f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Slider(
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.secondary,
            ),
            value = displayValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                val finalValue = dragValue
                dragValue = null
                if (finalValue != null) {
                    actionSink.sendAction(NowPlayingAction.SeekRequested(finalValue.toLong()))
                }
            },
            valueRange = 0f..maxValue,
            enabled = model.lengthMs > 0L,
        )

        CacheFillIndicator(cacheFraction = cacheFraction)
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatMs(displayValue.toLong()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = formatMs(model.lengthMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Thin secondary track below the seek slider showing how much of the active track is rendered
 * to the local cache and instantly readable. Horizontally padded by the slider's thumb radius
 * (~10dp in Material3) so its endpoints sit under the same x-range the active slider track uses.
 */
@Composable
private fun CacheFillIndicator(cacheFraction: Float) {
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .height(CacheBarHeight)
            .clip(RoundedCornerShape(CacheBarHeight / 2))
    ) {
        if (cacheFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(cacheFraction)
                    .background(fillColor),
            )
        }
    }
}

private val CacheBarHeight = 15.dp

@Suppress("LongMethod")
@Composable
private fun ColumnScope.TransportRow(
    model: NowPlayingModel,
    actionSink: ActionSink,
) {
    val accentTint = MaterialTheme.colorScheme.primary
    val mutedTint = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .height(72.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Where the shuffle toggle used to live: a placeholder for the future Setlist feature.
        // Shuffle itself now lives in the CONTROLS context menu.
        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.SetlistClicked) },
            modifier = Modifier
                .size(TransportToggleSize)
                .testTag(NOW_PLAYING_SETLIST_BUTTON_TAG),
        ) {
            Icon(
                imageVector = Icon.QueueMusic.vector(),
                contentDescription = null,
                tint = mutedTint,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.SkipBackClicked) },
            modifier = Modifier.size(TransportSkipSize),
        ) {
            Icon(
                imageVector = Icon.SkipPrevious.vector(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }

        Spacer(modifier = Modifier.size(16.dp))

        val isError = model.errorMessage != null
        Box(modifier = Modifier.size(TransportPlayPauseSize)) {
            IconButton(
                onClick = { actionSink.sendAction(NowPlayingAction.PlayPauseClicked) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (model.isBuffering) {
                    // Speaker is silent while the generator fills buffers. Swap the play/pause
                    // icon for a spinner so the wait reads as loading — the button still pauses
                    // on tap.
                    CircularProgressIndicator(
                        color = accentTint,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                    )
                } else {
                    Icon(
                        imageVector = when {
                            isError -> Icon.Warning.vector()
                            model.isPlaying -> Icon.Pause.vector()
                            else -> Icon.Play.vector()
                        },
                        contentDescription = null,
                        tint = if (isError) MaterialTheme.colorScheme.error else accentTint,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(16.dp))

        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.SkipForwardClicked) },
            enabled = model.canSkipForward,
            modifier = Modifier.size(TransportSkipSize),
        ) {
            Icon(
                imageVector = Icon.SkipNext.vector(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        // Where the repeat toggle used to live: opens the CONTROLS context menu (which now hosts
        // both the repeat and shuffle toggles).
        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.MenuClicked) },
            modifier = Modifier
                .size(TransportToggleSize)
                .testTag(NOW_PLAYING_MENU_BUTTON_TAG),
        ) {
            Icon(
                imageVector = Icon.Overflow.vector(),
                contentDescription = null,
                tint = mutedTint,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }
    }
}

private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / MS_PER_SECOND)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
