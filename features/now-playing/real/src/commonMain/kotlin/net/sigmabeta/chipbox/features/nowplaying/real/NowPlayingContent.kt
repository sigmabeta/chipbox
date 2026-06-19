package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.sigmabeta.sage.appcomm.ActionSink

private val ScreenPadding = 24.dp

// At/under this width the middle swaps between track info and the setlist; above it there's room
// to show both side by side.
private val WideLayoutBreakpoint = 780.dp

// Under this height there's no room for the tall cover-art + text layout, so the track info
// collapses to the compact MiniNowPlayingInfo card.
private val CompactHeightBreakpoint = 500.dp

@Composable
fun NowPlayingContent(
    model: NowPlayingModel,
    actionSink: ActionSink,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Above the breakpoint there's room to show the track info and the setlist together.
        val wide = maxWidth > WideLayoutBreakpoint
        // Under the height breakpoint the track info collapses to the compact mini card.
        val compact = maxHeight < CompactHeightBreakpoint

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(model, actionSink)

            Spacer(modifier = Modifier.height(16.dp))

            // Crossfade the whole middle when crossing the breakpoint so the layout doesn't snap.
            AnimatedContent(
                targetState = wide,
                label = "NowPlayingLayout",
                contentAlignment = Alignment.Center,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { isWide ->
                if (isWide) {
                    // Two panels: the info panel on the left, the setlist always on the right.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        InfoPanel(model, actionSink, compact, modifier = Modifier.weight(1f).fillMaxHeight())
                        NowPlayingSetlist(model, actionSink, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    // One panel: the setlist button swaps the info panel out for the setlist; the
                    // header and transport controls stay put.
                    AnimatedContent(
                        targetState = model.setlistVisible,
                        label = "NowPlayingMiddle",
                        contentAlignment = Alignment.Center,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier.fillMaxSize(),
                    ) { showingSetlist ->
                        if (showingSetlist) {
                            NowPlayingSetlist(model, actionSink, Modifier.fillMaxSize())
                        } else {
                            InfoPanel(model, actionSink, compact, Modifier.fillMaxSize())
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ProgressSection(model = model, actionSink = actionSink)

            Spacer(modifier = Modifier.height(16.dp))

            TransportRow(model = model, actionSink = actionSink)
        }
    }
}

/**
 * The primary panel's info mode: the full [NowPlayingInfo], or the compact [MiniNowPlayingInfo]
 * when the screen is too short for the tall cover-art layout.
 */
@Composable
private fun InfoPanel(model: NowPlayingModel, actionSink: ActionSink, compact: Boolean, modifier: Modifier) {
    // Crossfade between the full and compact info layouts when the height crosses the breakpoint.
    AnimatedContent(
        targetState = compact,
        label = "NowPlayingInfoPanel",
        contentAlignment = Alignment.Center,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier,
    ) { isCompact ->
        if (isCompact) {
            // The mini card sits centered in the panel region.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MiniNowPlayingInfo(model, actionSink)
            }
        } else {
            NowPlayingInfo(model, actionSink, Modifier.fillMaxSize())
        }
    }
}
