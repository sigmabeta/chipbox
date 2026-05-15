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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.chipbox.ui.components.utils.ImageSize
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.IconNameListModel
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.vector

@Composable
fun IconNameListItem(
    model: IconNameListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    IconNameListItem(
        model.name,
        model.icon,
        model.clickAction,
        model.active,
        actionSink,
        modifier,
        padding,
    )
}

@Composable
@Suppress("LongParameterList")
fun IconNameListItem(
    name: String,
    icon: Icon,
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
        label = "IconNameListItem.textColor",
    )
    val fontWeightValue by animateIntAsState(
        targetValue = if (active) FontWeight.Bold.weight else FontWeight.Normal.weight,
        label = "IconNameListItem.fontWeight",
    )
    val fontWeight = FontWeight(fontWeightValue)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { actionSink.sendAction(clickAction) }
            .padding(padding)
    ) {
        Icon(
            imageVector = icon.vector(),
            contentDescription = null,
            tint = textColor,
            modifier = Modifier
                .size(ImageSize.THUMBNAIL.size)
                .padding(8.dp)
                .align(Alignment.CenterVertically)
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
                .padding(vertical = 16.dp)
        )
    }
}

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Box(
            modifier = Modifier.background(
                color = MaterialTheme.colorScheme.background
            )
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
                color = MaterialTheme.colorScheme.background
            )
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
        IconNameListItem(
            IconNameListModel(
                dataId = 1234L,
                name = "Nintendo Entertainment System",
                icon = Icon.Chip,
                clickAction = SageAction.Noop,
                active = active,
            ),
            PreviewActionSink { },
            Modifier,
            PaddingValues(horizontal = 16.dp)
        )
        Button(
            onClick = { active = !active },
            modifier = Modifier.padding(8.dp),
        ) {
            Text(text = if (active) "Deactivate" else "Activate")
        }
    }
}
