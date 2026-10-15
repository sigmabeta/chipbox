package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.previews.SquareConstants
import net.sigmabeta.chipbox.common.ui.components.api.subs.CrossfadeImage
import net.sigmabeta.chipbox.common.ui.components.api.subs.ElevatedRoundRect
import net.sigmabeta.chipbox.common.ui.components.api.subs.TintedText
import net.sigmabeta.chipbox.common.ui.components.api.subs.LocalFirstContentReporter
import net.sigmabeta.chipbox.common.ui.components.api.subs.TraceSection
import net.sigmabeta.chipbox.common.ui.components.api.subs.gridCellOffscreenLayerEnabled
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.GridImageListModel

@Composable
fun GridImage(
    model: GridImageListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    // The first grid cell to compose is the library grid showing its first item; hosts that measure
    // startup (the Android benchmark build) provide a reporter that reports the app fully drawn.
    // The local is static, so the branch is stable within a host; production leave it null.
    val reportFirstContent = LocalFirstContentReporter.current
    if (reportFirstContent != null) {
        LaunchedEffect(reportFirstContent) { reportFirstContent() }
    }

    // Perfetto section the device scroll macrobenchmark sums as "GridImage" — the composition cost
    // of a grid cell, alongside the frame-time percentiles.
    TraceSection("GridImage") {
        GridImageContent(model, actionSink, modifier, padding)
    }
}

@Composable
private fun GridImageContent(
    model: GridImageListModel,
    actionSink: ActionSink,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val sourceInfo = model.sourceInfo

    val textColor = animateColorAsState(
        targetValue = if (model.active) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.White
        },
        label = "GridImage.textColor",
    )
    // Snapped, not animated: animating FontWeight re-measures the text every frame.
    val fontWeight = if (model.active) FontWeight.Bold else FontWeight.Normal

    // The cell's visuals, shared by both the plain and width-capped layouts below.
    val cell: @Composable () -> Unit = {
        Box {
            CrossfadeImage(
                sourceInfo = sourceInfo,
                imagePlaceholder = model.imagePlaceholder,
                contentDescription = model.name,
                modifier = Modifier.fillMaxSize(),
            )

            TintedText(
                text = model.name,
                color = textColor,
                fontWeight = fontWeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(brush = NameScrim)
                    .padding(NameInnerPadding)
                    .padding(top = NameExtraTopPadding), // For extra scrim
            )
        }
    }

    val cellModifier = Modifier
        .defaultMinSize(minWidth = SquareConstants.MIN_WIDTH)
        .aspectRatio(model.aspectRatio)
        // Cache the cell's rasterized contents into an offscreen Skia / GPU layer so scroll
        // translates the layer instead of re-rasterizing the image + scrim + text on every
        // frame. A Skiko (web + JVM) win — see [gridCellOffscreenLayerEnabled] — gated off on
        // Android, where the cached layer costs an extra GPU surface per visible cell without
        // a matching win.
        .then(if (gridCellOffscreenLayerEnabled) OffscreenCellLayer else Modifier)
        .clickable { actionSink.sendAction(model.clickAction) }

    val maxWidthDp = model.maxWidthDp
    if (maxWidthDp != null) {
        // Cap the cell's width and center it in the available space, so a single full-width item
        // (e.g. the "game of the day" hero) doesn't stretch across the whole screen.
        Box(
            modifier = modifier
                .padding(paddingValues = padding)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            ElevatedRoundRect(modifier = Modifier.widthIn(max = maxWidthDp.dp).then(cellModifier)) {
                cell()
            }
        }
    } else {
        ElevatedRoundRect(modifier = modifier.padding(paddingValues = padding).then(cellModifier)) {
            cell()
        }
    }
}

@Suppress("MagicNumber")
private val NameScrim = Brush.verticalGradient(
    colors = listOf(
        Color(0, 0, 0, 0),
        Color(0, 0, 0, 160),
        Color(0, 0, 0, 255),
    ),
)

private val NameInnerPadding = 8.dp
private val NameExtraTopPadding = 8.dp

// Reused (not rebuilt per cell) so every grid cell shares one stable offscreen-layer modifier.
private val OffscreenCellLayer = Modifier.graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}
