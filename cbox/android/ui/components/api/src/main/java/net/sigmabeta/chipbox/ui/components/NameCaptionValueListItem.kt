package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.NameCaptionValueListModel

@Composable
fun NameCaptionValueListItem(
    model: NameCaptionValueListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val primaryColor by animateColorAsState(
        targetValue = if (model.active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        label = "NameCaptionValueListItem.primaryColor",
    )
    val valueColor by animateColorAsState(
        targetValue = if (model.active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        },
        label = "NameCaptionValueListItem.valueColor",
    )
    val fontWeightValue by animateIntAsState(
        targetValue = if (model.active) FontWeight.Bold.weight else FontWeight.Normal.weight,
        label = "NameCaptionValueListItem.fontWeight",
    )
    val fontWeight = FontWeight(fontWeightValue)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(padding)
            .clickable { actionSink.sendAction(model.clickAction) },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight(),
        ) {
            Text(
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

            Text(
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

        Text(
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

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background,
            ),
        ) {
            Sample()
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background,
            ),
        ) {
            Sample()
        }
    }
}

@Composable
@Suppress("MagicNumber")
private fun Sample() {
    var active by remember { mutableStateOf(false) }

    Column {
        NameCaptionValueListItem(
            NameCaptionValueListModel(
                dataId = 1234L,
                name = "The Super Shinobi",
                caption = "Yuzo Koshiro",
                value = "2:18",
                clickAction = SageAction.Noop,
                active = active,
            ),
            PreviewActionSink { },
            Modifier,
            PaddingValues(horizontal = 8.dp),
        )
        Button(
            onClick = { active = !active },
            modifier = Modifier.padding(8.dp),
        ) {
            Text(text = if (active) "Deactivate" else "Activate")
        }
    }
}
