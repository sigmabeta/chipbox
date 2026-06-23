package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import net.sigmabeta.chipbox.common.ui.components.api.CrossfadeText
import net.sigmabeta.chipbox.common.ui.components.api.utils.FocusAreaShape
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.ui.Icon as SageIcon
import net.sigmabeta.sage.ui.vector

private val ErrorSectionTopPadding = 16.dp
private val ErrorSectionMaxHeight = 320.dp
private val ErrorRowSpacing = 8.dp
private val ErrorRowTextStartPadding = 16.dp
private val ErrorRowTextVerticalPadding = 12.dp
private const val ERROR_ROW_MAX_LINES = 2

/**
 * Rolling log of recent playback errors, sized to match the artwork above it. A [LazyColumn] so
 * each row can use [androidx.compose.foundation.lazy.LazyItemScope.animateItem] to fade and reflow
 * as errors are added, dismissed, or auto-cleared. The whole section animates in/out via the outer
 * [AnimatedVisibility] so it collapses cleanly (and its top padding disappears) when empty.
 */
@Composable
internal fun ColumnScope.ErrorSection(
    errors: List<NowPlayingError>,
    actionSink: ActionSink,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(ErrorRowSpacing),
        contentPadding = PaddingValues(top = ErrorSectionTopPadding),
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .heightIn(max = ErrorSectionMaxHeight),
    ) {
        // Newest first so the most recent failure is at the top of the log.
        items(
            items = errors.asReversed().toImmutableList(),
            key = { it.id },
            contentType = { "ErrorRow" },
        ) { error ->
            ErrorRow(
                error = error,
                actionSink = actionSink,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/**
 * A single error row, styled like SearchHistoryListItem but in the error palette: an
 * [androidx.compose.material3.ColorScheme.errorContainer] pill with
 * [androidx.compose.material3.ColorScheme.onErrorContainer] text and a clear button that dismisses
 * just this error.
 */
@Composable
private fun ErrorRow(
    error: NowPlayingError,
    actionSink: ActionSink,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(FocusAreaShape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = ErrorRowTextStartPadding),
    ) {
        CrossfadeText(
            text = error.message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = ERROR_ROW_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = ErrorRowTextVerticalPadding),
        )
        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.DismissErrorClicked(error.id)) },
        ) {
            Icon(
                imageVector = SageIcon.Clear.vector(),
                tint = MaterialTheme.colorScheme.onErrorContainer,
                contentDescription = null,
            )
        }
    }
}
