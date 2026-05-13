package net.sigmabeta.chipbox.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.ui.components.previews.PreviewActionSink
import net.sigmabeta.chipbox.ui.components.subs.CrossfadeImage
import net.sigmabeta.chipbox.ui.components.subs.ElevatedRoundRect
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.HeroImageListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.Icon

@Composable
@Suppress("MagicNumber", "LongMethod", "UnsafeCallOnNullableType")
fun BigImage(
    model: HeroImageListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    ElevatedRoundRect(
        modifier = modifier
            .padding(padding)
            .heightIn(max = 384.dp)
            .fillMaxWidth()
            .clickable(onClick = { actionSink.sendAction(model.clickAction) }),
        cornerRadius = 16.dp,
    ) {
        CrossfadeImage(
            sourceInfo = model.sourceInfo,
            imagePlaceholder = model.imagePlaceholder,
            contentDescription = model.contentDescription,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview
@Composable
private fun LoadingGame() {
    BigImage(
        HeroImageListModel(
            sourceInfo = SourceInfo("whatever"),
            imagePlaceholder = Icon.Album,
            contentDescription = "",
            clickAction = SageAction.Noop,
        ),
        PreviewActionSink { },
        modifier = Modifier,
        padding = PaddingValues(16.dp),
    )
}

@Preview
@Composable
private fun SuccessGame() {
    BigImage(
        HeroImageListModel(
            sourceInfo = SourceInfo("whatever"),
            imagePlaceholder = Icon.Description,
            contentDescription = "",
            clickAction = SageAction.Noop,
        ),
        PreviewActionSink { },
        modifier = Modifier,
        padding = PaddingValues(16.dp),
    )
}

@Preview
@Composable
private fun SuccessGameWithLabel() {
    BigImage(
        HeroImageListModel(
            sourceInfo = SourceInfo("whatever"),
            imagePlaceholder = Icon.Description,
            contentDescription = "",
            clickAction = SageAction.Noop,
        ),
        PreviewActionSink { },
        modifier = Modifier,
        padding = PaddingValues(16.dp),
    )
}
