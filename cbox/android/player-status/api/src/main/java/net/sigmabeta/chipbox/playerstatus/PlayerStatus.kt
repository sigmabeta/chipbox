package net.sigmabeta.chipbox.playerstatus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.sigmabeta.chipbox.ui.components.subs.CrossfadeImage
import net.sigmabeta.sage.ui.Icon as SageIcon

private val COMPACT_WIDTH_BREAKPOINT = 480.dp
private val HORIZONTAL_MARGIN = 8.dp
private val VERTICAL_MARGIN = 16.dp
private val MAX_WIDTH = 400.dp
private val CONTAINER_HEIGHT = 72.dp
private val CARD_SHAPE_RADIUS = 12.dp
private const val SCRIM_ALPHA = 0.25f
private const val ANIM_DURATION_MS = 300

@Composable
fun PlayerStatus(
    modifier: Modifier = Modifier,
    viewModel: PlayerStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val widthDp = LocalConfiguration.current.screenWidthDp.dp
    val widthModifier = if (widthDp >= COMPACT_WIDTH_BREAKPOINT) {
        Modifier.widthIn(max = MAX_WIDTH)
    } else {
        Modifier.fillMaxWidth()
    }

    AnimatedVisibility(
        visible = state.visible,
        enter = slideInVertically(
            animationSpec = tween(ANIM_DURATION_MS),
            initialOffsetY = { it },
        ) + fadeIn(animationSpec = tween(ANIM_DURATION_MS)),
        exit = slideOutVertically(
            animationSpec = tween(ANIM_DURATION_MS),
            targetOffsetY = { it },
        ) + fadeOut(animationSpec = tween(ANIM_DURATION_MS)),
        modifier = modifier
            .then(widthModifier)
            .padding(horizontal = HORIZONTAL_MARGIN, vertical = VERTICAL_MARGIN),
    ) {
        PlayerStatusCard(
            state = state,
            onPlayPauseClicked = viewModel::onPlayPauseClicked,
        )
    }
}

@Composable
private fun PlayerStatusCard(
    state: PlayerStatusState,
    onPlayPauseClicked: () -> Unit,
) {
    var imageLoaded by remember(state.artwork.info) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(CARD_SHAPE_RADIUS),
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(CONTAINER_HEIGHT),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.artwork.info != null) {
                CrossfadeImage(
                    sourceInfo = state.artwork,
                    imagePlaceholder = SageIcon.MUSIC_NOTE,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    onImageLoadedChange = { imageLoaded = it },
                )
                if (imageLoaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color.Black.copy(alpha = SCRIM_ALPHA)
                            ),
                    )
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                PlayerStatusInfo(
                    name = state.title,
                    caption = state.artistsCaption,
                    onArtworkBackground = imageLoaded,
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                        .padding(horizontal = 12.dp),
                )

                IconButton(
                    onClick = onPlayPauseClicked,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}
