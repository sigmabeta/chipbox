package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import net.sigmabeta.chipbox.images.api.BitmapGenerator
import net.sigmabeta.sage.images.SourceInfo

/**
 * Android actual: the historical `BitmapGenerator` body, so recorded Paparazzi goldens stay
 * byte-identical to the pre-shared-renderer output.
 */
internal actual val chipboxPreviewImage: (@Composable (SourceInfo, Modifier) -> Unit)? =
    { sourceInfo, modifier ->
        val bitmap = BitmapGenerator.generateBitmap(sourceInfo.info.toString())
        Image(
            painter = BitmapPainter(image = bitmap, filterQuality = FilterQuality.None),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
