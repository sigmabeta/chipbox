package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.utils.FocusAreaShape
import net.sigmabeta.chipbox.common.ui.components.api.utils.innerFocusPadding
import net.sigmabeta.chipbox.common.ui.components.api.utils.outerFocusPadding
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.SearchHistoryListModel
import net.sigmabeta.sage.ui.Icon as SageIcon
import net.sigmabeta.sage.ui.vector

@Composable
fun SearchHistoryListItem(
    model: SearchHistoryListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(padding.outerFocusPadding())
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(FocusAreaShape)
            .clickable { actionSink.sendAction(model.clickAction) }
            .padding(padding.innerFocusPadding(omitEnd = true)),
    ) {
        val contentColor = MaterialTheme.colorScheme.onBackground

        CrossfadeText(
            text = model.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1.0f)
                .padding(vertical = 12.dp)
        )
        IconButton(
            onClick = { actionSink.sendAction(model.removeAction) }
        ) {
            Icon(
                imageVector = SageIcon.Clear.vector(),
                tint = contentColor,
                contentDescription = null,
            )
        }
    }
}
