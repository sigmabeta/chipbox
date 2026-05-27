package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.previews.PreviewActionSink
import net.sigmabeta.sage.appcomm.SageAction
import net.sigmabeta.sage.components.HeroImageListModel
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.Icon

@Preview
@Composable
private fun LoadingGame() {
    HeroImage(
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
    HeroImage(
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
    HeroImage(
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
