package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.vector

private val SWIPE_REVEAL_PADDING = 24.dp

/**
 * Wraps [content] so a right-to-left swipe removes it, invoking [onRemove] once the swipe is
 * confirmed. Reveals an error-tinted panel with a remove icon behind the row as it slides. The row
 * paints an opaque background while swiping so the reveal only shows in the gap the row opens, not
 * through the (otherwise transparent) row content.
 *
 * Generic counterpart to the inline swipe in the now-playing setlist; used by
 * [net.sigmabeta.chipbox.common.ui.list.api.ChipboxReorderableEntry] for dismissable
 * [DraggableListModel] rows.
 */
@Composable
fun SwipeToRemoveBox(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            val removed = value == SwipeToDismissBoxValue.EndToStart
            if (removed) onRemove()
            removed
        },
    )
    val swiping = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = { if (swiping) SwipeRemoveBackground() },
        modifier = modifier,
    ) {
        Box(
            modifier = if (swiping) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier,
        ) {
            content()
        }
    }
}

/** The reveal behind a row being swiped away: an error-tinted panel with a remove icon. */
@Composable
private fun SwipeRemoveBackground() {
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = SWIPE_REVEAL_PADDING),
    ) {
        Icon(
            imageVector = Icon.Clear.vector(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
