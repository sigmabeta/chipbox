package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.utils.ImageSize
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.vector

/**
 * Wraps any other list-item composable with a drag handle appended to its right.
 *
 * The wrapped row's own layout is untouched — [content] renders exactly as it would standalone,
 * taking all the width left of the handle. Only the handle carries [dragHandle], the ready-made
 * `Modifier` that [net.sigmabeta.sage.ui.list.ReorderableScreen] hands each row, so the drag
 * gesture is confined to the handle and the rest of the row keeps its normal click behaviour.
 */
@Composable
fun DraggableListItem(
    dragHandle: Modifier,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            content()
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icon.Menu.vector(),
            contentDescription = DRAG_HANDLE_DESCRIPTION,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = dragHandle
                .size(ImageSize.THUMBNAIL.size)
                .padding(8.dp),
        )
    }
}

private const val DRAG_HANDLE_DESCRIPTION = "Drag to reorder"
