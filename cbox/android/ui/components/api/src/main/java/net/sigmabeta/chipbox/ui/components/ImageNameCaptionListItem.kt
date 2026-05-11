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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.chipbox.ui.components.subs.CrossfadeImage
import net.sigmabeta.chipbox.ui.components.subs.ElevatedCircle
import net.sigmabeta.chipbox.ui.components.utils.ImageSize
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreviewMenu
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.ImageNameCaptionListModel
import net.sigmabeta.sage.components.SearchResultListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.Icon

@Composable
fun ImageNameCaptionListItem(
    model: ImageNameCaptionListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    ImageNameCaptionListItem(
        model.name,
        model.caption,
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
fun ImageNameCaptionListItem(
    model: SearchResultListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    ImageNameCaptionListItem(
        model.name,
        model.caption,
        model.sourceInfo,
        model.imagePlaceholder,
        model.clickAction,
        false,
        actionSink,
        modifier,
        padding,
    )
}

@Composable
@Suppress("LongMethod", "MagicNumber", "LongParameterList")
private fun ImageNameCaptionListItem(
    name: String,
    caption: String,
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
        label = "ImageNameCaptionListItem.textColor",
    )
    val fontWeightValue by animateIntAsState(
        targetValue = if (active) FontWeight.Bold.weight else FontWeight.Normal.weight,
        label = "ImageNameCaptionListItem.fontWeight",
    )
    val fontWeight = FontWeight(fontWeightValue)

    Row(
        modifier = modifier
            .padding(padding)
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { actionSink.sendAction(clickAction) }

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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                fontWeight = fontWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }
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

@Preview
@Composable
private fun Menu() {
    ChipboxPreviewMenu {
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
        ImageNameCaptionListItem(
            ImageNameCaptionListModel(
                dataId = 1234L,
                name = "Xenoblade Chronicles 3",
                caption = "Yasunori Mitsuda, Mariam Abounnasr, Manami Kiyota, ACE+, Kenji Hiramatsu",
                sourceInfo = SourceInfo("https://randomfox.ca/images/12.jpg"),
                imagePlaceholder = Icon.PERSON,
                actionableId = null,
                clickAction = SageAction.Noop,
                active = active,
            ),
            PreviewActionSink {},
            Modifier,
            PaddingValues(horizontal = 8.dp)
        )
        Button(
            onClick = { active = !active },
            modifier = Modifier.padding(8.dp),
        ) {
            Text(text = if (active) "Deactivate" else "Activate")
        }
    }
}
