package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.subs.CrossfadeImage
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.Icon
import net.sigmabeta.sage.ui.vector

private val ScreenPadding = 24.dp
private val ArtworkCornerRadius = 16.dp
private val TransportPlayPauseSize = 80.dp
private val TransportSkipSize = 64.dp
private val TransportToggleSize = 48.dp

@Composable
internal fun NowPlayingContent(
    model: NowPlayingModel,
    actionSink: ActionSink,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(model, actionSink)

        Spacer(modifier = Modifier.height(16.dp))

        Artwork(model)

        Spacer(modifier = Modifier.height(16.dp))

        TrackInfo(model)

        Spacer(modifier = Modifier.height(16.dp))

        ProgressSection(model = model, actionSink = actionSink)

        Spacer(modifier = Modifier.height(16.dp))

        TransportRow(model = model, actionSink = actionSink)
    }
}

@Composable
private fun ColumnScope.TopBar(model: NowPlayingModel, actionSink: ActionSink) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.BackClicked) },
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
            )
        }

        Column(
            modifier = Modifier.weight(1.0f)
        ) {
            if (model.sessionTypeLabel.isNotEmpty()) {
                Text(
                    text = model.sessionTypeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (model.sessionSourceName.isNotEmpty()) {
                    Text(
                        text = model.sessionSourceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.PlayerSettingsClicked) },
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun ColumnScope.Artwork(model: NowPlayingModel) {
    Surface(
        shape = RoundedCornerShape(ArtworkCornerRadius),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(ArtworkCornerRadius)),
    ) {
        val errorMessage = model.errorMessage
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // On a fatal error, force the artwork into its error treatment and overlay a short
            // message; the surrounding metadata still names the track that failed. This is a
            // stand-in for the dedicated artwork-error component, to be swapped in once it lands.
            CrossfadeImage(
                sourceInfo = model.artwork,
                imagePlaceholder = Icon.MusicNote,
                contentDescription = null,
                simulateError = errorMessage != null,
                modifier = Modifier.fillMaxSize(),
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ScreenPadding),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.TrackInfo(model: NowPlayingModel) {
    Text(
        text = model.title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )

    if (model.artistsCaption.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = model.artistsCaption,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (model.gameTitle.isNotEmpty()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = model.gameTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColumnScope.ProgressSection(
    model: NowPlayingModel,
    actionSink: ActionSink,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val maxValue = model.lengthMs.coerceAtLeast(1L).toFloat()
    val displayValue = (dragValue ?: model.positionMs.toFloat()).coerceIn(0f, maxValue)

    Slider(
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
        modifier = Modifier.fillMaxWidth(),
    )

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

@Suppress("LongMethod")
@Composable
private fun ColumnScope.TransportRow(
    model: NowPlayingModel,
    actionSink: ActionSink,
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
        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.ShuffleClicked) },
            modifier = Modifier.size(TransportToggleSize),
        ) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = null,
                tint = if (model.isShuffled) accentTint else mutedTint,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.SkipBackClicked) },
            modifier = Modifier.size(TransportSkipSize),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }

        Spacer(modifier = Modifier.size(16.dp))

        val isError = model.errorMessage != null
        Box(modifier = Modifier.size(TransportPlayPauseSize)) {
            IconButton(
                onClick = { actionSink.sendAction(NowPlayingAction.PlayPauseClicked) },
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = when {
                        isError -> Icon.Warning.vector()
                        model.isPlaying -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error else accentTint,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.size(16.dp))

        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.SkipForwardClicked) },
            enabled = model.canSkipForward,
            modifier = Modifier.size(TransportSkipSize),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.RepeatClicked) },
            modifier = Modifier.size(TransportToggleSize),
        ) {
            Icon(
                imageVector = if (model.repeatMode == RepeatMode.ONE) {
                    Icons.Filled.RepeatOne
                } else {
                    Icons.Filled.Repeat
                },
                contentDescription = null,
                tint = if (model.repeatMode == RepeatMode.OFF) mutedTint else accentTint,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }
    }
}

private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / MS_PER_SECOND)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "%d:%02d".format(minutes, seconds)
}
