package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.subs.TintedText
import net.sigmabeta.chipbox.common.ui.components.api.utils.FocusAreaShape
import net.sigmabeta.chipbox.common.ui.components.api.utils.innerFocusPadding
import net.sigmabeta.chipbox.common.ui.components.api.utils.outerFocusPadding
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.NameCaptionValueListModel

@Composable
fun NameCaptionValueListItem(
    model: NameCaptionValueListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val primaryColor = animateColorAsState(
        targetValue = if (model.active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        label = "NameCaptionValueListItem.primaryColor",
    )
    val valueColor = animateColorAsState(
        targetValue = if (model.active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        },
        label = "NameCaptionValueListItem.valueColor",
    )
    // Snapped, not animated: animating FontWeight re-measures the text every frame.
    val fontWeight = if (model.active) FontWeight.Bold else FontWeight.Normal

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(padding.outerFocusPadding())
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(FocusAreaShape)
            .clickable { actionSink.sendAction(model.clickAction) }
            .padding(padding.innerFocusPadding()),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight(),
        ) {
            TintedText(
                text = model.name,
                style = MaterialTheme.typography.titleMedium,
                color = primaryColor,
                fontWeight = fontWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .paddingFromBaseline(top = 24.dp),
            )

            Spacer(modifier = Modifier.height(4.dp))

            TintedText(
                text = model.caption,
                style = MaterialTheme.typography.bodySmall,
                color = primaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .paddingFromBaseline(bottom = 12.dp),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        TintedText(
            text = model.value,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.wrapContentWidth(),
        )
    }
}
