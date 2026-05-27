package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.previews.BigImageConstants
import net.sigmabeta.chipbox.common.ui.components.api.previews.CoverArtConstants
import net.sigmabeta.chipbox.common.ui.components.api.previews.NotifConstants
import net.sigmabeta.chipbox.common.ui.components.api.previews.SquareConstants
import net.sigmabeta.chipbox.common.ui.components.api.previews.WideItemConstants
import net.sigmabeta.chipbox.common.ui.components.api.subs.ElevatedRoundRect
import net.sigmabeta.chipbox.common.ui.components.api.subs.Flasher
import net.sigmabeta.sage.components.LoadingType
import kotlin.random.Random

@Composable
@Suppress("MagicNumber")
fun LoadingItem(
    seed: Long,
    loadingType: LoadingType,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val randomizer = Random(seed)
    val randomDelay = randomizer.nextInt(200)

    val (width, ratio) = when (loadingType) {
        LoadingType.PAGE -> PAGE_MIN_WIDTH.dp to PAGE_ASPECT_RATIO

        LoadingType.SQUARE -> SquareConstants.MIN_WIDTH to SquareConstants.ASPECT_RATIO

        // Same minimum width as SQUARE, but the cover-art 3:4 ratio so games' loading
        // placeholders don't pop from square → portrait when the data arrives.
        LoadingType.COVER -> SquareConstants.MIN_WIDTH to CoverArtConstants.ASPECT_RATIO

        LoadingType.NOTIF -> NotifConstants.MIN_WIDTH to NotifConstants.ASPECT_RATIO

        LoadingType.WIDE_ITEM -> WideItemConstants.MIN_WIDTH to WideItemConstants.ASPECT_RATIO

        LoadingType.BIG_IMAGE -> BigImageConstants.MIN_WIDTH to BigImageConstants.ASPECT_RATIO

        else -> return
    }

    ElevatedRoundRect(
        modifier = modifier
            .padding(paddingValues = padding)
            .defaultMinSize(minWidth = width)
            .aspectRatio(ratio),
    ) {
        Flasher(startDelay = randomDelay)
    }
}

private const val PAGE_MIN_WIDTH = 300
private const val PAGE_ASPECT_RATIO = 0.77272725f
