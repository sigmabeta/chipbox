package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.sigmabeta.chipbox.common.ui.components.api.previews.CoverArtConstants
import net.sigmabeta.chipbox.common.ui.components.api.subs.CrossfadeImage
import net.sigmabeta.chipbox.common.ui.components.api.subs.ElevatedRoundRect
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.HeroImageListModel

@Composable
fun HeroImage(
    model: HeroImageListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    // IGDB covers are 3:4 portrait. Fill the available width and let the height follow the
    // ratio — same width-driven sizing as the grid tiles and the BIG_IMAGE loading placeholder.
    ElevatedRoundRect(
        modifier = modifier
            .padding(padding)
            .fillMaxWidth()
            .aspectRatio(CoverArtConstants.ASPECT_RATIO)
            .clickable(onClick = { actionSink.sendAction(model.clickAction) }),
    ) {
        CrossfadeImage(
            sourceInfo = model.sourceInfo,
            imagePlaceholder = model.imagePlaceholder,
            contentDescription = model.contentDescription,
            modifier = Modifier.fillMaxSize(),
            loadOriginalSize = true,
        )
    }
}
