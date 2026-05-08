package net.sigmabeta.chipbox.ui.components

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.chipbox.ui.components.subs.CrossfadeImage
import net.sigmabeta.chipbox.ui.components.previews.ChipboxPreview
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.WideItemListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.Icon

@Composable
fun WideItem(
    model: WideItemListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val shape = RoundedCornerShape(8.dp)

    val commonModifier = modifier
        .padding(padding)
        .padding(vertical = 4.dp)
        .defaultMinSize(minWidth = 192.dp)
        .height(64.dp)

    val actualModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        commonModifier.shadow(
            elevation = 4.dp,
            shape = shape
        )
    } else {
        commonModifier.clip(shape)
    }

    Row(
        modifier = actualModifier
            .clickable { actionSink.sendAction(model.clickAction) }
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        CrossfadeImage(
            sourceInfo = SourceInfo(model.sourceInfo),
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
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(8.dp)
                .align(CenterVertically)
        )
    }
}

@Preview
@Composable
private fun Light() {
    ChipboxPreview {
        Sample()
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Dark() {
    ChipboxPreview {
        Sample()
    }
}

@Composable
@Suppress("MagicNumber")
private fun Sample() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .wrapContentSize()
            .background(
                color = MaterialTheme.colorScheme.background
            )
            .padding(16.dp)
    ) {
        WideItem(
            WideItemListModel(
                1234L,
                "Konami Kukeiha Club",
                "https://randomfox.ca/images/12.jpg",
                Icon.PERSON,
                null,
                SageAction.Noop
            ),
            PreviewActionSink {},
            modifier = Modifier,
            padding = PaddingValues(horizontal = 8.dp)
        )

        WideItem(
            WideItemListModel(
                1234L,
                "Masayoshi Soken",
                "https://randomfox.ca/images/12.jpg",
                Icon.PERSON,
                null,
                SageAction.Noop
            ),
            PreviewActionSink {},
            modifier = Modifier,
            padding = PaddingValues(horizontal = 8.dp)
        )
    }
}
