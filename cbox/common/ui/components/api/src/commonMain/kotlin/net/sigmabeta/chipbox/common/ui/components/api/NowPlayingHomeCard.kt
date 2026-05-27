package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.subs.CrossfadeImage
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.ui.Icon as SageIcon

private val CARD_HEIGHT = 200.dp
private val CARD_SHAPE_RADIUS = 16.dp
private val CARD_SHADOW_ELEVATION = 8.dp
private val PLAY_BUTTON_SIZE = 64.dp
private const val SCRIM_ALPHA = 0.45f
private const val TEXT_SHADOW_ALPHA = 1f
private val TEXT_SHADOW_OFFSET_Y = 2.dp
private val TEXT_SHADOW_BLUR = 4.dp

/**
 * Larger sibling of [net.sigmabeta.chipbox.common.playerstatus.api.PlayerStatus] for use as a
 * Home row. Same layered composition — artwork → scrim → text + play/pause — but taller and
 * with two distinct click targets: the card itself opens now-playing, the embedded button
 * toggles playback. Duplicates the mini-player visual rather than calling it directly because
 * the mini-player composable lives downstream.
 */
@Composable
fun NowPlayingHomeCard(
    model: NowPlayingHomeCardListModel,
    actionSink: ActionSink,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(),
) {
    var imageLoaded by remember(model.artwork.info) { mutableStateOf(false) }

    val foregroundColor by animateColorAsState(
        targetValue = if (imageLoaded) Color.White else MaterialTheme.colorScheme.primary,
        label = "NowPlayingHomeCard.foregroundColor",
    )
    val shadowAlpha by animateFloatAsState(
        targetValue = if (imageLoaded) TEXT_SHADOW_ALPHA else 0f,
        label = "NowPlayingHomeCard.textShadowAlpha",
    )
    val density = LocalDensity.current
    val textShadow = Shadow(
        color = Color.Black.copy(alpha = shadowAlpha),
        offset = Offset(0f, with(density) { TEXT_SHADOW_OFFSET_Y.toPx() }),
        blurRadius = with(density) { TEXT_SHADOW_BLUR.toPx() },
    )

    Surface(
        shape = RoundedCornerShape(CARD_SHAPE_RADIUS),
        tonalElevation = 3.dp,
        shadowElevation = CARD_SHADOW_ELEVATION,
        modifier = modifier
            .fillMaxWidth()
            .padding(padding)
            .height(CARD_HEIGHT)
            .clickable { actionSink.sendAction(model.clickAction) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CardArtwork(
                model = model,
                onImageLoaded = { imageLoaded = it },
            )
            CardForeground(
                model = model,
                actionSink = actionSink,
                foregroundColor = foregroundColor,
                textShadow = textShadow,
            )
        }
    }
}

@Composable
private fun CardArtwork(
    model: NowPlayingHomeCardListModel,
    onImageLoaded: (Boolean) -> Unit,
) {
    // Crossfade keyed on the artwork so the outgoing image (and its scrim) keep rendering
    // through a swap. Mirrors PlayerStatusCard's structure.
    Crossfade(
        targetState = model.artwork,
        modifier = Modifier.fillMaxSize(),
        label = "NowPlayingHomeCard.artwork",
    ) { artwork ->
        if (artwork.info != null) {
            var branchLoaded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxSize()) {
                CrossfadeImage(
                    sourceInfo = artwork,
                    imagePlaceholder = SageIcon.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    onImageLoadedChange = {
                        branchLoaded = it
                        onImageLoaded(it)
                    },
                )
                if (branchLoaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = SCRIM_ALPHA)),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardForeground(
    model: NowPlayingHomeCardListModel,
    actionSink: ActionSink,
    foregroundColor: Color,
    textShadow: Shadow,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = model.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = foregroundColor,
                    shadow = textShadow,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (model.artistsCaption.isNotEmpty()) {
                Text(
                    text = model.artistsCaption,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = foregroundColor,
                        shadow = textShadow,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = { actionSink.sendAction(model.playPauseAction) },
            modifier = Modifier.size(PLAY_BUTTON_SIZE),
        ) {
            if (model.isBuffering) {
                CircularProgressIndicator(
                    color = foregroundColor,
                    modifier = Modifier.size(PLAY_BUTTON_SIZE).padding(12.dp),
                )
            } else {
                Icon(
                    imageVector = when {
                        model.isError -> Icons.Filled.Warning
                        model.isPlaying -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = null,
                    tint = if (model.isError) MaterialTheme.colorScheme.error else foregroundColor,
                    modifier = Modifier.size(PLAY_BUTTON_SIZE).padding(12.dp),
                )
            }
        }
    }
}
