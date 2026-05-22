package net.sigmabeta.chipbox.common.ui.components.api.subs

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import net.sigmabeta.chipbox.images.api.BitmapGenerator
import net.sigmabeta.sage.images.SourceInfo

@Composable
internal actual fun FakeImage(sourceInfo: SourceInfo, modifier: Modifier) {
    val bitmap = BitmapGenerator.generateBitmap(sourceInfo.info.toString())
    Image(
        painter = BitmapPainter(image = bitmap, filterQuality = FilterQuality.None),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
