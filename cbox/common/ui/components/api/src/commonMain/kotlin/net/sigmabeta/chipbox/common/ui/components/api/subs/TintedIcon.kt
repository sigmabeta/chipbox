package net.sigmabeta.chipbox.common.ui.components.api.subs

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The icon sibling of [TintedText], for list rows / grid cells that tint an icon with an animated
 * colour. Unlike text, an icon's tint can't be deferred to the draw phase without changing how
 * Material's [Icon] rasterises the vector, so instead this leaf reads the [State] itself: only
 * this icon recomposes when the tint animates, not the row/cell that hosts it.
 */
@Composable
fun TintedIcon(
    imageVector: ImageVector,
    tint: State<Color>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint.value,
        modifier = modifier,
    )
}
