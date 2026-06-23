package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.SmallTextListModel

@Composable
fun SmallText(
    model: SmallTextListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(padding)
            .padding(bottom = 8.dp, top = 8.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = SmallTextShape
            )
            .clip(SmallTextShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { actionSink.sendAction(model.clickAction) }
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp),
    ) {
        CrossfadeText(
            text = model.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val SmallTextShape = RoundedCornerShape(8.dp)
