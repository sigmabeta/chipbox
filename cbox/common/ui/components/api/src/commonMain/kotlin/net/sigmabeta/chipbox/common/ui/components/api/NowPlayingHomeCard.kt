package net.sigmabeta.chipbox.common.ui.components.api

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.subs.CrossfadeImage
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.ui.Icon as SageIcon
import net.sigmabeta.sage.ui.vector

private val CARD_HEIGHT = 200.dp
private val CARD_SHAPE_RADIUS = 16.dp
private val CARD_SHADOW_ELEVATION = 8.dp
private val PLAY_BUTTON_SIZE = 64.dp
private const val SCRIM_ALPHA = 0.45f
private const val TEXT_SHADOW_ALPHA = 1f
private val TEXT_SHADOW_OFFSET_Y = 2.dp
private val TEXT_SHADOW_BLUR = 4.dp

// Mirrors the navigation rail breakpoint in ChipboxNavHost — below this the layout shows a
// bottom nav bar (no rail), so the card has roughly screen-width to render. Drop the title
// down a typography step at that size so it doesn't crowd two lines + caption + button.
private val COMPACT_WIDTH_BREAKPOINT = 480.dp

// Wide-screen affordance: at or above this width the card has room for a progress bar
// between the title block and the play/pause button without crowding either.
private val PROGRESS_BAR_BREAKPOINT = 600.dp
private const val TEXT_WEIGHT_WITH_PROGRESS = 1f
private const val TEXT_WEIGHT_NO_PROGRESS = 1f
private const val PROGRESS_BAR_WEIGHT = 1f
private val PROGRESS_BAR_HORIZONTAL_PADDING = 16.dp
private const val TRACK_ALPHA = 0.3f

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
    // Tell the host that this card is on-screen so it can suppress the bottom mini-player
    // (and restore it on dispose). The card doesn't know what "suppress mini-player" entails
    // — it just routes appear/disappear through the standard action sink with whatever
    // actions the model carries. Mirrors the click/play-pause action pattern.
    //
    // The appear emits from a LaunchedEffect rather than a DisposableEffect setup so it's
    // deferred to a dispatcher tick. ChipboxListEntry's `viewModel.events.collect(...)`
    // also launches as a coroutine on the same dispatcher; when both this card and the
    // outer entry re-enter composition together (e.g. popping back from NowPlaying), the
    // entry's collect-coroutine is dispatched first and subscribes before our appear
    // coroutine emits. A synchronous DisposableEffect setup would otherwise fire the
    // sendAction during the apply phase, before any subscriber exists — and the event
    // would be silently dropped (MutableSharedFlow loses emissions made without subscribers).
    LaunchedEffect(actionSink, model.appearAction) {
        actionSink.sendAction(model.appearAction)
    }
    DisposableEffect(actionSink, model.disappearAction) {
        // Disappear fires from onDispose because the unmount order is child-then-parent —
        // by the time we send this, ChipboxListEntry's collector is still alive (its LE
        // hasn't been cancelled yet), so the event delivers fine synchronously.
        onDispose { actionSink.sendAction(model.disappearAction) }
    }

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
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Use the card's own width as a proxy for "screen big enough for a nav rail".
            // At the breakpoint and above, the layout already gave room to the rail, so the
            // card width is reduced — but still wide enough for the larger title style.
            val titleStyle = if (maxWidth < COMPACT_WIDTH_BREAKPOINT) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.headlineSmall
            }
            val showProgressBar = maxWidth >= PROGRESS_BAR_BREAKPOINT
            CardArtwork(
                model = model,
                onImageLoaded = { imageLoaded = it },
            )
            CardForeground(
                model = model,
                actionSink = actionSink,
                foregroundColor = foregroundColor,
                textShadow = textShadow,
                titleStyle = titleStyle,
                showProgressBar = showProgressBar,
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
                    // Same bucketed-loading behaviour as GridImage — Coil's
                    // ConstraintsSizeResolver + GridImageSize bucketing keeps the decoded
                    // bitmap proportionate to the rendered card rather than the source's
                    // raw resolution. Stated explicitly (it's already the default) so the
                    // intent is obvious next to HeroImage's `loadOriginalSize = true`.
                    loadOriginalSize = false,
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

@Suppress("LongMethod")
@Composable
private fun CardForeground(
    model: NowPlayingHomeCardListModel,
    actionSink: ActionSink,
    foregroundColor: Color,
    textShadow: Shadow,
    titleStyle: TextStyle,
    showProgressBar: Boolean,
) {
    Row(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val textWeight = if (showProgressBar) TEXT_WEIGHT_WITH_PROGRESS else TEXT_WEIGHT_NO_PROGRESS
        Column(
            modifier = Modifier
                .animateContentSize()
                .weight(textWeight)
                .padding(bottom = 16.dp)
                .padding(start = 16.dp),
        ) {
            CrossfadeText(
                text = model.title,
                style = titleStyle.copy(
                    color = foregroundColor,
                    shadow = textShadow,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize()
            )

            if (model.artistsCaption.isNotEmpty()) {
                CrossfadeText(
                    text = model.artistsCaption,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = foregroundColor,
                        shadow = textShadow,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.animateContentSize()
                )
            }
        }
        // Fade the progress bar in/out at the breakpoint. Pure fade (no size animation) —
        // the bar's weight slot snaps in/out under it rather than easing the text column's
        // width through an intermediate state. Mirrors how the bottom mini-player handles
        // its own appearance/disappearance via [AnimatedVisibility] in
        // [net.sigmabeta.chipbox.common.playerstatus.api.PlayerStatus].
        AnimatedVisibility(
            visible = showProgressBar,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.weight(PROGRESS_BAR_WEIGHT),
        ) {
            LinearProgressIndicator(
                progress = { model.progressFraction },
                color = foregroundColor,
                trackColor = foregroundColor.copy(alpha = TRACK_ALPHA),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PROGRESS_BAR_HORIZONTAL_PADDING),
            )
        }
        IconButton(
            onClick = { actionSink.sendAction(model.playPauseAction) },
            modifier = Modifier.size(PLAY_BUTTON_SIZE),
        ) {
            val buttonModifier = Modifier
                .size(PLAY_BUTTON_SIZE)
                .padding(horizontal = 16.dp)

            if (model.isBuffering) {
                CircularProgressIndicator(
                    color = foregroundColor,
                    modifier = buttonModifier,
                )
            } else {
                Icon(
                    imageVector = when {
                        model.isError -> SageIcon.Warning.vector()
                        model.isPlaying -> SageIcon.Pause.vector()
                        else -> SageIcon.Play.vector()
                    },
                    contentDescription = null,
                    tint = if (model.isError) MaterialTheme.colorScheme.error else foregroundColor,
                    modifier = buttonModifier,
                )
            }
        }
    }
}
