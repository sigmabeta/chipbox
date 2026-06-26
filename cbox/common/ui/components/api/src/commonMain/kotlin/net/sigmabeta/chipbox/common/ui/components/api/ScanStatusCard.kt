package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.sage.appcomm.ActionSink

/**
 * Renders [ScanStatusCardListModel] as a bounded two-column card. Left: coarse status + the file
 * currently being read (scanning only). Right: a vertically scrolling list of the supplied detail
 * rows (live changes / final summary) or a wrapping failure message. The background colour animates
 * to a success/error tint when the scan finishes.
 */
@Composable
fun ScanStatusCard(
    model: ScanStatusCardListModel,
    actionSink: ActionSink,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(),
) {
    val containerTarget = when (model.status) {
        ScanCardStatus.SCANNING -> MaterialTheme.colorScheme.surfaceContainer
        ScanCardStatus.COMPLETE -> MaterialTheme.colorScheme.tertiaryContainer
        ScanCardStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (model.status) {
        ScanCardStatus.SCANNING -> MaterialTheme.colorScheme.onSurface
        ScanCardStatus.COMPLETE -> MaterialTheme.colorScheme.onTertiaryContainer
        ScanCardStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
    }
    val containerColor by animateColorAsState(targetValue = containerTarget, label = "ScanStatusCard.containerColor")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(padding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(CARD_CORNER_RADIUS),
            color = containerColor,
            modifier = Modifier
                .widthIn(max = CARD_MAX_WIDTH)
                .fillMaxWidth()
                .height(CARD_HEIGHT),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize().padding(CARD_PADDING)) {
                    StatusColumn(
                        model = model,
                        contentColor = contentColor,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = COLUMN_GAP),
                    )
                    DetailColumn(
                        detail = model.detail,
                        contentColor = contentColor,
                        actionSink = actionSink,
                    )
                }
                // Once the scan has settled, a tap anywhere dismisses the card. The overlay sits on
                // top so it captures taps over the (non-interactive) summary/error rows too — hence
                // "anywhere". Absent while scanning, so the live change rows stay tappable.
                val dismissAction = model.dismissAction
                if (dismissAction != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { actionSink.sendAction(dismissAction) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusColumn(
    model: ScanStatusCardListModel,
    contentColor: Color,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        // Anchor the status + file to the bottom of the fixed-height card.
        verticalArrangement = Arrangement.Bottom,
    ) {
        // Animate only on a status change; the (much more frequent) file updates recompose in
        // place, so they tick without re-running the enter/exit transition.
        AnimatedContent(
            targetState = model.statusLabel,
            label = "ScanStatusCard.statusText",
        ) { label ->
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor,
                    maxLines = STATUS_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                // Folder above file: the coarser "where" over the faster-moving "what".
                val folder = model.currentFolder
                if (folder != null) {
                    Spacer(modifier = Modifier.height(CURRENT_FILE_GAP))
                    Text(
                        text = folder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val file = model.currentFile
                if (file != null) {
                    Spacer(modifier = Modifier.height(CURRENT_FILE_GAP))
                    Text(
                        text = file,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.DetailColumn(
    detail: ScanStatusDetail,
    contentColor: Color,
    actionSink: ActionSink,
) {
    val columnModifier = Modifier
        .weight(1f)
        .fillMaxHeight()
    when (detail) {
        is ScanStatusDetail.Rows -> LazyColumn(
            modifier = columnModifier,
            verticalArrangement = Arrangement.spacedBy(DETAIL_ROW_GAP),
        ) {
            items(items = detail.items, key = { it.dataId }) { item ->
                item.Content(sink = actionSink, debug = false, mod = Modifier, pad = PaddingValues())
            }
        }

        is ScanStatusDetail.Error -> Box(modifier = columnModifier.verticalScroll(rememberScrollState())) {
            Text(
                text = detail.message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

private val CARD_MAX_WIDTH = 600.dp
private val CARD_HEIGHT = 300.dp
private val CARD_CORNER_RADIUS = 16.dp
private val CARD_PADDING = 16.dp
private val COLUMN_GAP = 16.dp
private val CURRENT_FILE_GAP = 8.dp
private val DETAIL_ROW_GAP = 4.dp
private const val STATUS_MAX_LINES = 2
