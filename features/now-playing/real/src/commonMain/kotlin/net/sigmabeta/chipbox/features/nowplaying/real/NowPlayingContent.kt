package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.foundation.lazy.rememberLazyListState
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

// Shared-element keys so a pane that exists in two layout states (e.g. the setlist as the lone
// narrow panel and as the wide right panel) animates its bounds between them instead of
// fade-out/in — it slides/resizes across.
private const val INFO_PANE_KEY = "now-playing-info-pane"
private const val SETLIST_PANE_KEY = "now-playing-setlist-pane"

/** Which pane(s) fill the flexible middle. The single [AnimatedContent] over this drives the
 *  shared-element transitions between layouts. */
private enum class MiddleLayout { WIDE, NARROW_INFO, NARROW_SETLIST }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingContent(
    model: NowPlayingModel,
    actionSink: ActionSink,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Above the width breakpoint there's room for both panes; under the height breakpoint the
        // info pane collapses to the compact mini card.
        val wide = maxWidth > WideLayoutBreakpoint
        val compact = maxHeight < CompactHeightBreakpoint
        val layout = when {
            wide -> MiddleLayout.WIDE
            model.setlistVisible -> MiddleLayout.NARROW_SETLIST
            else -> MiddleLayout.NARROW_INFO
        }

        // Hoisted above the AnimatedContent so the setlist keeps its scroll position as it moves
        // between the lone narrow panel and the wide right panel.
        val setlistListState = rememberLazyListState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(model, actionSink)

            Spacer(modifier = Modifier.height(16.dp))

            // One AnimatedContent over the whole layout state lets shared elements tween a pane's
            // bounds between layouts: panes present in both states slide/resize across; a pane that
            // appears only in the target (e.g. the info pane when widening from the setlist) fades in.
            SharedTransitionLayout(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AnimatedContent(
                    targetState = layout,
                    label = "NowPlayingLayout",
                    contentAlignment = Alignment.Center,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.fillMaxSize(),
                ) { state ->
                    when (state) {
                        MiddleLayout.WIDE -> Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            InfoPanel(
                                model = model,
                                actionSink = actionSink,
                                compact = compact,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .sharedElement(rememberSharedContentState(INFO_PANE_KEY), this@AnimatedContent),
                            )
                            NowPlayingSetlist(
                                model = model,
                                actionSink = actionSink,
                                listState = setlistListState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .sharedElement(rememberSharedContentState(SETLIST_PANE_KEY), this@AnimatedContent),
                            )
                        }

                        MiddleLayout.NARROW_SETLIST -> NowPlayingSetlist(
                            model = model,
                            actionSink = actionSink,
                            listState = setlistListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .sharedElement(rememberSharedContentState(SETLIST_PANE_KEY), this@AnimatedContent),
                        )

                        MiddleLayout.NARROW_INFO -> InfoPanel(
                            model = model,
                            actionSink = actionSink,
                            compact = compact,
                            modifier = Modifier
                                .fillMaxSize()
                                .sharedElement(rememberSharedContentState(INFO_PANE_KEY), this@AnimatedContent),
                        )
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
