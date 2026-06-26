package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.components.SectionListModel

@Composable
@Suppress("MagicNumber")
fun SectionListItem(
    model: SectionListModel,
    actionSink: ActionSink,
    showDebug: Boolean,
    modifier: Modifier,
    padding: PaddingValues,
) {
    val maxWidth = model.maxContentWidthDp
    if (maxWidth != null) {
        // Cap the content to maxWidth and centre it horizontally in the full-width slot. Inset the
        // slot by the screen side margin so a backgroundContainer panel doesn't bleed to the screen
        // edges; the inner items then sit flush to the panel (zeroed `padding`) so they keep the
        // same on-screen position instead of being double-inset.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            SectionContentColumn(
                model = model,
                actionSink = actionSink,
                showDebug = showDebug,
                padding = PaddingValues(),
                modifier = Modifier.widthIn(max = maxWidth.dp),
            )
        }
    } else {
        SectionContentColumn(
            model = model,
            actionSink = actionSink,
            showDebug = showDebug,
            padding = padding,
            modifier = modifier,
        )
    }
}

@Composable
private fun SectionContentColumn(
    model: SectionListModel,
    actionSink: ActionSink,
    showDebug: Boolean,
    padding: PaddingValues,
    modifier: Modifier,
) {
    // Opt-in rounded `surfaceContainer` panel, matching the Now Playing setlist/info panes.
    val panelModifier = if (model.backgroundContainer) {
        Modifier
            .clip(RoundedCornerShape(PANEL_CORNER_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(PANEL_CONTENT_PADDING)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .then(panelModifier)
    ) {
        model.sectionItems.forEach {
            it.Content(
                sink = actionSink,
                debug = showDebug,
                mod = Modifier,
                pad = padding
            )
        }
    }
}

private val PANEL_CORNER_RADIUS = 16.dp
private val PANEL_CONTENT_PADDING = 8.dp
