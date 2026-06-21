package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.DraggableListItem
import net.sigmabeta.chipbox.common.ui.components.api.IconNameListItem
import net.sigmabeta.chipbox.common.ui.components.api.NameCaptionValueListItem
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.NameCaptionValueListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.vector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
internal fun NowPlayingSetlist(
    model: NowPlayingModel,
    actionSink: ActionSink,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    // Local mirror mutated live during a drag; rebuilt whenever the VM re-emits the queue order
    // (data-class row equality means routine playback ticks don't churn it). Mirrors ReorderableScreen.
    val items = remember(model.setlist) { model.setlist.toMutableStateList() }
    var dragStart by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    // listState is hoisted by the caller so scroll position survives the pane moving between layouts.
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        items.add(to.index, items.removeAt(from.index))
    }
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        modifier = modifier
            .widthIn(min = NowPlayingPanelMinWidth, max = NowPlayingPanelMaxWidth)
            .clip(RoundedCornerShape(NowPlayingPanelCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // A non-reorderable header row to capture the whole setlist into a playlist. Hidden for the
        // All Tracks session (and an empty queue) — see [NowPlayingModel.canAddSetlistToPlaylist].
        if (model.canAddSetlistToPlaylist) {
            item(key = "setlist-add-to-playlist") {
                IconNameListItem(
                    name = model.setlistAddToPlaylistLabel,
                    icon = Icon.QueueMusic,
                    clickAction = NowPlayingAction.AddSetlistToPlaylistClicked,
                    active = false,
                    actionSink = actionSink,
                    modifier = Modifier.testTag(NOW_PLAYING_SETLIST_ADD_PLAYLIST_TAG),
                    padding = NowPlayingRowPadding,
                )
            }
        }
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
            padding = NowPlayingRowPadding,
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
