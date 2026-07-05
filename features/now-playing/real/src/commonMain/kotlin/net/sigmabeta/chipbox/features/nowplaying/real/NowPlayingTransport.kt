package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.CrossfadeText
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.vector

private val TransportPlayPauseSize = 80.dp
private val TransportSkipSize = 64.dp
private val TransportToggleSize = 48.dp
private val CacheBarHeight = 15.dp
private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L

@Composable
internal fun ColumnScope.TopBar(model: NowPlayingModel, actionSink: ActionSink) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.BackClicked) },
        ) {
            Icon(
                imageVector = Icon.Caret.vector(),
                contentDescription = null,
            )
        }

        Column(
            modifier = Modifier.weight(1.0f)
        ) {
            if (model.sessionTypeLabel.isNotEmpty()) {
                CrossfadeText(
                    text = model.sessionTypeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textModifier = Modifier.fillMaxWidth(),
                )
                if (model.sessionSourceName.isNotEmpty()) {
                    CrossfadeText(
                        text = model.sessionSourceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textModifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Temporarily hidden — kept in the layout (invisible, non-interactive) so it holds its
        // slot and the title column stays centered. Restore visibility when the overflow returns.
        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.PlayerSettingsClicked) },
            modifier = Modifier.alpha(0f),
            enabled = false,
        ) {
            Icon(
                imageVector = Icon.Overflow.vector(),
                contentDescription = null,
            )
        }
    }
}

@Composable
internal fun ColumnScope.ProgressSection(
    model: NowPlayingModel,
    actionSink: ActionSink,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val maxValue = model.lengthMs.coerceAtLeast(1L).toFloat()
    val displayValue = (dragValue ?: model.positionMs.toFloat()).coerceIn(0f, maxValue)
    val cacheFraction = (model.cachedMs.toFloat() / maxValue).coerceIn(0f, 1f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Slider(
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.secondary,
            ),
            value = displayValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                val finalValue = dragValue
                dragValue = null
                if (finalValue != null) {
                    actionSink.sendAction(NowPlayingAction.SeekRequested(finalValue.toLong()))
                }
            },
            valueRange = 0f..maxValue,
            enabled = model.lengthMs > 0L,
        )

        CacheFillIndicator(cacheFraction = cacheFraction)
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatMs(displayValue.toLong()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = formatMs(model.lengthMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Thin secondary track below the seek slider showing how much of the active track is rendered
 * to the local cache and instantly readable. Horizontally padded by the slider's thumb radius
 * (~10dp in Material3) so its endpoints sit under the same x-range the active slider track uses.
 */
@Composable
private fun CacheFillIndicator(cacheFraction: Float) {
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .height(CacheBarHeight)
            .clip(RoundedCornerShape(CacheBarHeight / 2))
    ) {
        if (cacheFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(cacheFraction)
                    .background(fillColor),
            )
        }
    }
}

@Composable
internal fun ColumnScope.TransportRow(
    model: NowPlayingModel,
    actionSink: ActionSink,
    setlistPinned: Boolean = false,
) {
    val accentTint = MaterialTheme.colorScheme.primary
    val mutedTint = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .height(72.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The leftmost transport slot toggles the inline setlist — except in the wide layout, where
        // the setlist is already pinned as its own panel, so the toggle would be redundant. There we
        // surface the current track's favorite toggle instead (a duplicate of the CONTROLS row).
        if (setlistPinned) {
            TransportIconButton(
                icon = if (model.isTrackFavorite) Icon.FavoriteFilled else Icon.FavoriteEmpty,
                action = NowPlayingAction.AddToFavoritesClicked,
                actionSink = actionSink,
                size = TransportToggleSize,
                // Accent the button while the track is favorited, muted otherwise — same on/off
                // colouring the CONTROLS rows use for their active state.
                tint = if (model.isTrackFavorite) accentTint else mutedTint,
                modifier = Modifier.testTag(NOW_PLAYING_FAVORITES_BUTTON_TAG),
            )
        } else {
            TransportIconButton(
                icon = Icon.QueueMusic,
                action = NowPlayingAction.SetlistClicked,
                actionSink = actionSink,
                size = TransportToggleSize,
                tint = mutedTint,
                modifier = Modifier.testTag(NOW_PLAYING_SETLIST_BUTTON_TAG),
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        TransportIconButton(
            icon = Icon.SkipPrevious,
            action = NowPlayingAction.SkipBackClicked,
            actionSink = actionSink,
            size = TransportSkipSize,
        )

        Spacer(modifier = Modifier.size(16.dp))

        val isError = model.errorMessage != null
        Box(modifier = Modifier.size(TransportPlayPauseSize)) {
            IconButton(
                onClick = { actionSink.sendAction(NowPlayingAction.PlayPauseClicked) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (model.isBuffering) {
                    // Speaker is silent while the generator fills buffers. Swap the play/pause
                    // icon for a spinner so the wait reads as loading — the button still pauses
                    // on tap.
                    CircularProgressIndicator(
                        color = accentTint,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                    )
                } else {
                    Icon(
                        imageVector = when {
                            isError -> Icon.Warning.vector()
                            model.isPlaying -> Icon.Pause.vector()
                            else -> Icon.Play.vector()
                        },
                        contentDescription = null,
                        tint = if (isError) MaterialTheme.colorScheme.error else accentTint,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(16.dp))

        TransportIconButton(
            icon = Icon.SkipNext,
            action = NowPlayingAction.SkipForwardClicked,
            actionSink = actionSink,
            size = TransportSkipSize,
            enabled = model.canSkipForward,
        )

        Spacer(modifier = Modifier.size(8.dp))

        // Where the repeat toggle used to live: opens the CONTROLS context menu (which now hosts
        // both the repeat and shuffle toggles).
        TransportIconButton(
            icon = Icon.Overflow,
            action = NowPlayingAction.MenuClicked,
            actionSink = actionSink,
            size = TransportToggleSize,
            tint = mutedTint,
            modifier = Modifier.testTag(NOW_PLAYING_MENU_BUTTON_TAG),
        )
    }
}

/** A text-less transport button: a sized [IconButton] whose [icon] dispatches [action] on tap. */
@Composable
private fun TransportIconButton(
    icon: Icon,
    action: NowPlayingAction,
    actionSink: ActionSink,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    IconButton(
        onClick = { actionSink.sendAction(action) },
        enabled = enabled,
        modifier = modifier.size(size),
    ) {
        Icon(
            imageVector = icon.vector(),
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / MS_PER_SECOND)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
