package net.sigmabeta.chipbox.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.SquareConstants
import net.sigmabeta.chipbox.ui.components.subs.CrossfadeImage
import net.sigmabeta.chipbox.ui.components.subs.ElevatedRoundRect
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.SquareItemListModel
import net.sigmabeta.sage.images.SourceInfo
import androidx.compose.runtime.getValue


@Composable
fun SquareItem(
    model: SquareItemListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val sourceInfo = remember(model.sourceInfo) { SourceInfo(model.sourceInfo) }

    val textColor by animateColorAsState(
        targetValue = if (model.active) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.White
        },
        label = "SquareItem.textColor",
    )
    val fontWeightValue by animateIntAsState(
        targetValue = if (model.active) FontWeight.Bold.weight else FontWeight.Normal.weight,
        label = "SquareItem.fontWeight",
    )
    val fontWeight = FontWeight(fontWeightValue)

    ElevatedRoundRect(
        modifier = modifier
            .padding(paddingValues = padding)
            .defaultMinSize(minWidth = SquareConstants.MIN_WIDTH)
            .aspectRatio(SquareConstants.ASPECT_RATIO)
            .clickable { actionSink.sendAction(model.clickAction) }
    ) {
        Box {
            CrossfadeImage(
                sourceInfo = sourceInfo,
                imagePlaceholder = model.imagePlaceholder,
                contentDescription = model.name,
                modifier = Modifier.fillMaxSize(),
            )

            Text(
                text = model.name,
                color = textColor,
                fontWeight = fontWeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(brush = NameScrim)
                    .padding(NameInnerPadding)
                    .padding(top = NameExtraTopPadding), // For extra scrim
            )
        }
    }
}

@Suppress("MagicNumber")
private val NameScrim = Brush.verticalGradient(
    colors = listOf(
        Color(0, 0, 0, 0),
        Color(0, 0, 0, 160),
        Color(0, 0, 0, 255),
    ),
)

private val NameInnerPadding = 8.dp
private val NameExtraTopPadding = 8.dp
