package net.sigmabeta.chipbox.ui.components.api

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.api.subs.CrossfadeImage
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.WideItemListModel
import net.sigmabeta.sage.images.SourceInfo
import androidx.compose.runtime.getValue

@Composable
fun WideItem(
    model: WideItemListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val sourceInfo = remember(model.sourceInfo) { SourceInfo(model.sourceInfo) }

    val textColor by animateColorAsState(
        targetValue = if (model.active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "WideItem.textColor",
    )
    val fontWeightValue by animateIntAsState(
        targetValue = if (model.active) FontWeight.Bold.weight else FontWeight.Normal.weight,
        label = "WideItem.fontWeight",
    )
    val fontWeight = FontWeight(fontWeightValue)

    Row(
        modifier = modifier
            .padding(padding)
            .padding(vertical = 4.dp)
            .defaultMinSize(minWidth = 192.dp)
            .height(64.dp)
            .shadow(elevation = 4.dp, shape = WideItemShape)
            .clickable { actionSink.sendAction(model.clickAction) }
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        CrossfadeImage(
            sourceInfo = sourceInfo,
            imagePlaceholder = model.imagePlaceholder,
            contentDescription = null,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1.0f)
        )

        Text(
            text = model.name,
            textAlign = TextAlign.Start,
            maxLines = 2,
            color = textColor,
            fontWeight = fontWeight,
            style = MaterialTheme.typography.titleMedium,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(8.dp)
                .align(CenterVertically)
        )
    }
}

private val WideItemShape = RoundedCornerShape(8.dp)
