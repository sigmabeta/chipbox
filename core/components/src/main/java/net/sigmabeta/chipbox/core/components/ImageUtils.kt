package net.sigmabeta.chipbox.core.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil.compose.ImagePainter


@Composable
fun RealImage(
    title: String,
    coilPainter: ImagePainter,
    @StringRes contentDescriptionId: Int
) {
    Image(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.0f),
        contentDescription = stringResource(contentDescriptionId, title),
        painter = coilPainter,
        contentScale = ContentScale.Crop
    )
}
