package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.ui.chrome.LocalChromeController
import net.sigmabeta.chipbox.ui.chrome.ScreenChrome
import net.sigmabeta.chipbox.ui.components.subs.CrossfadeImage
import net.sigmabeta.chipbox.ui.freeform.ChipboxFreeformEntry
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.ui.Icon as SageIcon

private val ScreenPadding = 24.dp
private val ArtworkCornerRadius = 16.dp
private val TransportPlayPauseSize = 80.dp
private val TransportSkipSize = 64.dp

@Composable
fun NowPlayingRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chromeController = LocalChromeController.current
    LaunchedEffect(Unit) {
        chromeController.set(
            ScreenChrome(
                showTopBar = false,
                showPlayerStatus = false
            )
        )
    }

    val viewModel: NowPlayingViewModel = hiltViewModel()
    ChipboxFreeformEntry(viewModel, onEvent, modifier) { model, actionSink, _, m ->
        NowPlayingContent(model, actionSink, m)
    }
}

@Composable
private fun NowPlayingContent(
    model: NowPlayingModel,
    actionSink: ActionSink,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(ArtworkCornerRadius),
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(ArtworkCornerRadius)),
        ) {
            CrossfadeImage(
                sourceInfo = model.artwork,
                imagePlaceholder = SageIcon.MusicNote,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

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

        Spacer(modifier = Modifier.height(24.dp))

        ProgressSection(model = model, actionSink = actionSink)

        Spacer(modifier = Modifier.height(16.dp))

        TransportRow(model = model, actionSink = actionSink)
    }
}

@Composable
private fun ProgressSection(
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

@Composable
private fun TransportRow(
    model: NowPlayingModel,
    actionSink: ActionSink,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { actionSink.sendAction(NowPlayingAction.SkipBackClicked) },
            modifier = Modifier.size(TransportSkipSize),
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )
        }

        Spacer(modifier = Modifier.size(16.dp))

        Box(modifier = Modifier.size(TransportPlayPauseSize)) {
            IconButton(
                onClick = { actionSink.sendAction(NowPlayingAction.PlayPauseClicked) },
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = if (model.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
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
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
