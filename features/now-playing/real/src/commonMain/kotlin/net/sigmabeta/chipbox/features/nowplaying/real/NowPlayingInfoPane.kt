package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.sigmabeta.chipbox.common.ui.components.api.previews.CoverArtConstants
import net.sigmabeta.chipbox.common.ui.components.api.subs.CrossfadeImage
import net.sigmabeta.sage.appcomm.ActionSink
import net.sigmabeta.sage.ui.Icon

private val ArtworkCornerRadius = 16.dp
private val TrackInfoMinWidth = 256.dp
private val TrackInfoInteriorPadding = 8.dp
private const val MINI_CARD_SCRIM_ALPHA = 0.45f

/** The "now playing" panel: cover art, the error log, and the track-info / context-menu block. */
@Composable
internal fun NowPlayingInfo(model: NowPlayingModel, actionSink: ActionSink, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Artwork(model)

        ErrorSection(errors = model.errors, actionSink = actionSink)

        // Half the former 16dp gap above the info block lives inside TrackInfo's tap target
        // (TrackInfoInteriorPadding); the other half is this exterior gap.
        Spacer(modifier = Modifier.height(8.dp))

        InfoContainer(model, actionSink)
    }
}

@Composable
private fun ColumnScope.Artwork(model: NowPlayingModel) {
    // IGDB covers are 3:4 portrait. Take the available vertical space, then center a 3:4
    // cover sized off that height so the whole cover shows instead of being cropped.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(ArtworkCornerRadius),
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(CoverArtConstants.ASPECT_RATIO)
                .clip(RoundedCornerShape(ArtworkCornerRadius)),
        ) {
            CrossfadeImage(
                sourceInfo = model.artwork,
                imagePlaceholder = Icon.MusicNote,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The container shared by [TrackInfo] and the [ContextMenu]: it wraps whichever is showing (between
 * [InfoContainerMinWidth] and [InfoContainerMaxWidth]) and its background animates from transparent
 * (plain track info) to `surfaceContainer` (any open menu). `animateContentSize` morphs the size as
 * the content swaps, so the `weight(1f)` artwork above reflows smoothly instead of jumping.
 */
@Composable
private fun InfoContainer(model: NowPlayingModel, actionSink: ActionSink) {
    val backgroundColor by animateColorAsState(
        targetValue = if (model.contextMenuMode == ContextMenuMode.NONE) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        label = "NowPlayingInfoBackground",
    )

    // AnimatedContent (not Crossfade) so the content stays centered while the container resizes
    // between the track info and the larger menu: Crossfade's box pins its layers to the top-left,
    // which left the returning TrackInfo stranded in the corner until the resize finished. The
    // built-in SizeTransform animates the size, so the weight(1f) artwork above still reflows.
    AnimatedContent(
        targetState = model.contextMenuMode,
        label = "NowPlayingInfo",
        contentAlignment = Alignment.Center,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = Modifier
            .clip(RoundedCornerShape(InfoContainerCornerRadius))
            .background(backgroundColor),
    ) { mode ->
        // `mode` is the animating layer's own target, not necessarily model.contextMenuMode, so
        // render off it — the outgoing layer keeps showing its old state while it fades.
        if (mode == ContextMenuMode.NONE) {
            TrackInfo(model, actionSink)
        } else {
            ContextMenu(model, mode, actionSink)
        }
    }
}

@Composable
private fun TrackInfo(model: NowPlayingModel, actionSink: ActionSink) {
    // The whole block — interior padding included — is one tap target that opens the LINKS menu.
    // Wraps its content (down to TrackInfoMinWidth) rather than filling; the shared container's
    // AnimatedContent keeps it centered while the container resizes.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = TrackInfoMinWidth)
            .testTag(NOW_PLAYING_TRACK_INFO_TAG)
            .clickable { actionSink.sendAction(NowPlayingAction.TrackInfoClicked) }
            .padding(TrackInfoInteriorPadding),
    ) {
        Text(
            text = model.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
            )
        }
    }
}

/**
 * The standalone compact now-playing card shown in place of [NowPlayingInfo] on short screens —
 * layered artwork → scrim → title/artist text — but without the transport controls. Tapping it
 * opens the LINKS menu, like [TrackInfo], and it carries the same test tag. The fixed-height card is
 * centered in the panel region.
 */
@Composable
internal fun MiniNowPlayingInfo(model: NowPlayingModel, actionSink: ActionSink) {
    var imageLoaded by remember(model.artwork.info) { mutableStateOf(false) }
    val foregroundColor by animateColorAsState(
        targetValue = if (imageLoaded) Color.White else MaterialTheme.colorScheme.onSurface,
        label = "MiniNowPlayingInfo.foreground",
    )

    Surface(
        shape = RoundedCornerShape(InfoContainerCornerRadius),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(NOW_PLAYING_TRACK_INFO_TAG)
            .clickable { actionSink.sendAction(NowPlayingAction.TrackInfoClicked) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CrossfadeImage(
                sourceInfo = model.artwork,
                imagePlaceholder = Icon.MusicNote,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                onImageLoadedChange = { imageLoaded = it },
            )
            // Scrim only once a real image is behind the text, for legibility.
            if (imageLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = MINI_CARD_SCRIM_ALPHA)),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = foregroundColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (model.artistsCaption.isNotEmpty()) {
                    Text(
                        text = model.artistsCaption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = foregroundColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
