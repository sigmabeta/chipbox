@file:Suppress("MagicNumber")

package net.sigmabeta.chipbox.common.ui.components.api.utils

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val MIN_INNER_PADDING = 8.dp

/** Rounded shape clipped around a clickable list row so its focus/press highlight is rounded. */
val FocusAreaShape = RoundedCornerShape(8.dp)

/**
 * Half the supplied horizontal padding plus the full vertical padding, applied OUTSIDE a row's
 * focus area so the highlight is inset from the screen edge. Pair with [innerFocusPadding].
 */
@Composable
fun PaddingValues.outerFocusPadding(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) / 2,
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection) / 2,
        bottom = calculateBottomPadding(),
    )
}

/**
 * The other half of the horizontal padding, applied INSIDE the focus area and floored at
 * [MIN_INNER_PADDING] so content keeps breathing room even when the caller supplies little or no
 * horizontal padding (e.g. the grid render path hands items an empty [PaddingValues]). Pair with
 * [outerFocusPadding].
 */
@Composable
fun PaddingValues.innerFocusPadding(
    omitStart: Boolean = false,
    omitEnd: Boolean = false,
): PaddingValues = PaddingValues(
        start = unlessOmitted(omitStart),
        end = unlessOmitted(omitEnd),
    )

@Composable
private fun PaddingValues.unlessOmitted(
    omit: Boolean,
): Dp = if (omit) {
    0.dp
} else {
    val layoutDirection = LocalLayoutDirection.current
    (calculateEndPadding(layoutDirection) / 2).coerceAtLeast(MIN_INNER_PADDING)
}
