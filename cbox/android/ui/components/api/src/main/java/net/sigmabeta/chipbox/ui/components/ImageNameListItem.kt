package net.sigmabeta.chipbox.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.subs.CrossfadeImage
import net.sigmabeta.chipbox.ui.components.subs.ElevatedCircle
import net.sigmabeta.chipbox.ui.components.utils.ImageSize
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.ImageNameListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.Icon
import androidx.compose.runtime.getValue


@Composable
fun ImageNameListItem(
    model: ImageNameListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    ImageNameListItem(
        model.name,
        model.sourceInfo,
        model.imagePlaceholder,
        model.clickAction,
        model.active,
        actionSink,
        modifier,
        padding,
    )
}

@Composable
@Suppress("MagicNumber", "LongParameterList")
fun ImageNameListItem(
    name: String,
    sourceInfo: SourceInfo,
    imagePlaceholder: Icon,
    clickAction: SageAction,
    active: Boolean,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val textColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        label = "ImageNameListItem.textColor",
    )
    val fontWeightValue by animateIntAsState(
        targetValue = if (active) FontWeight.Bold.weight else FontWeight.Normal.weight,
        label = "ImageNameListItem.fontWeight",
    )
    val fontWeight = FontWeight(fontWeightValue)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { actionSink.sendAction(clickAction) }
            .padding(paddingValues = padding)
            .padding(vertical = 4.dp)
    ) {
        ElevatedCircle(
            modifier = Modifier
                .size(ImageSize.THUMBNAIL.size)
                .align(Alignment.CenterVertically)
        ) {
            CrossfadeImage(
                sourceInfo = sourceInfo,
                imagePlaceholder = imagePlaceholder,
                contentDescription = null,
                modifier = Modifier,
            )
        }

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterVertically)
        )
    }
}
