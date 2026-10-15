package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.subs.CrossfadeImage
import net.sigmabeta.chipbox.common.ui.components.api.subs.TintedText
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.WideItemListModel

@Composable
fun WideItem(
    model: WideItemListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val sourceInfo = model.sourceInfo

    val textColor = animateColorAsState(
        targetValue = if (model.active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "WideItem.textColor",
    )
    // Snapped, not animated: animating FontWeight re-measures the text every frame.
    val fontWeight = if (model.active) FontWeight.Bold else FontWeight.Normal

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

        TintedText(
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
