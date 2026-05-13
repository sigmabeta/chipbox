package net.sigmabeta.chipbox.playerstatus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
private val CARD_SHADOW_ELEVATION = 6.dp
private const val SCRIM_ALPHA = 0.4f
private const val TEXT_SHADOW_ALPHA = 1f
private val TEXT_SHADOW_OFFSET_Y = 2.dp
private val TEXT_SHADOW_BLUR = 4.dp
private const val ANIM_DURATION_MS = 300

/**
 * Vertical space the PlayerStatus reserves at the bottom of the screen when visible —
 * card height plus its top and bottom margins. Callers can use this to inset content
 * so it doesn't get hidden behind the bar.
 */
val PlayerStatusReservedHeight: Dp = CONTAINER_HEIGHT + VERTICAL_MARGIN * 2

/** Animation duration used by the bar's slide-in/out — exposed for syncing parent insets. */
const val PlayerStatusAnimDurationMs: Int = ANIM_DURATION_MS

@Composable
fun PlayerStatus(
    modifier: Modifier = Modifier,
    onVisibleChange: (Boolean) -> Unit = {},
    onClick: () -> Unit = {},
    viewModel: PlayerStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.visible) {
        onVisibleChange(state.visible)
    }

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
            onClick = onClick,
        )
    }
}

@Composable
private fun PlayerStatusCard(
    state: PlayerStatusState,
    onPlayPauseClicked: () -> Unit,
    onClick: () -> Unit,
) {
    var imageLoaded by remember(state.artwork.info) { mutableStateOf(false) }

    val foregroundColor by animateColorAsState(
        targetValue = if (imageLoaded) {
            Color.White
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "PlayerStatus.foregroundColor",
    )

    val shadowAlpha by animateFloatAsState(
        targetValue = if (imageLoaded) TEXT_SHADOW_ALPHA else 0f,
        label = "PlayerStatus.textShadowAlpha",
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
        modifier = Modifier
            .fillMaxWidth()
            .height(CONTAINER_HEIGHT)
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Crossfade keyed on `state.artwork` so the outgoing artwork (and its scrim) keeps
            // rendering during the fade-out — without this, removing the if-gate's subtree on
            // the same frame as `info` going null produces a hard cut.
            Crossfade(
                targetState = state.artwork,
                modifier = Modifier.fillMaxSize(),
                label = "PlayerStatus.artwork",
            ) { artwork ->
                if (artwork.info != null) {
                    // Local to this branch so the scrim stays visible while the branch fades
                    // out. The outer `imageLoaded` is reset on every artwork change to drive
                    // text-color animation off of the target (not the displayed) image.
                    var branchImageLoaded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        CrossfadeImage(
                            sourceInfo = artwork,
                            imagePlaceholder = SageIcon.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            onImageLoadedChange = {
                                branchImageLoaded = it
                                imageLoaded = it
                            },
                        )
                        if (branchImageLoaded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Color.Black.copy(alpha = SCRIM_ALPHA)
                                    ),
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                PlayerStatusInfo(
                    name = state.title,
                    caption = state.artistsCaption,
                    textColor = foregroundColor,
                    textShadow = textShadow,
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
                        tint = foregroundColor,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}
