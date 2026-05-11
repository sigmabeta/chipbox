package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.chipbox.ui.components.previews.SquareConstants
import net.sigmabeta.chipbox.ui.components.subs.CrossfadeImage
import net.sigmabeta.chipbox.ui.components.subs.ElevatedRoundRect
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.SquareItemListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.Icon

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

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
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
            modifier = Modifier
                .fillMaxSize()
                .background(
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

    Column(modifier = Modifier.padding(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SquareItem(
                SquareItemListModel(
                    dataId = 1234L,
                    name = "Xenoblade Chronicles 3",
                    sourceInfo = "https://randomfox.ca/images/12.jpg",
                    imagePlaceholder = Icon.ALBUM,
                    actionableId = null,
                    clickAction = SageAction.Noop,
                    active = active,
                ),
                PreviewActionSink {},
                Modifier.weight(1.0f),
                PaddingValues(horizontal = 8.dp)
            )

            SquareItem(
                SquareItemListModel(
                    dataId = 1235L,
                    name = "Xenoblade Chronicles 3: Future Redeemed Some More",
                    sourceInfo = "https://randomfox.ca/images/1235.jpg",
                    imagePlaceholder = Icon.ALBUM,
                    actionableId = null,
                    clickAction = SageAction.Noop,
                ),
                PreviewActionSink {},
                Modifier.weight(1.0f),
                PaddingValues(horizontal = 8.dp)
            )
        }
        Button(
            onClick = { active = !active },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(text = if (active) "Deactivate first" else "Activate first")
        }
    }
}
