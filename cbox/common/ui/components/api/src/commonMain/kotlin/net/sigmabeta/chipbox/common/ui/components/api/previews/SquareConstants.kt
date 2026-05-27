package net.sigmabeta.chipbox.common.ui.components.api.previews

import androidx.compose.ui.unit.dp

object SquareConstants {
    // Square (1:1) — used by LoadingType.SQUARE for the artist-list loading placeholder.
    // Cover-art grids (games) use LoadingType.COVER which pulls CoverArtConstants's 3:4.
    const val ASPECT_RATIO = 1f
    val MIN_WIDTH = 160.dp
}
