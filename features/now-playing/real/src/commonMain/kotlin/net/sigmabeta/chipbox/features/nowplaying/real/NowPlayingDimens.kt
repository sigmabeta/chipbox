package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

// TrackInfo and the ContextMenu share one container (rounded shape + the animated background), but
// keep their own width bounds: TrackInfo wraps its content down to a 256dp floor (no cap); the menu
// keeps its 200..600dp range.
internal val ContextMenuMinWidth = 200.dp
internal val ContextMenuMaxWidth = 600.dp
internal val InfoContainerCornerRadius = 16.dp
internal val ContextMenuRowPadding = PaddingValues(horizontal = 8.dp)

/**
 * Test tags for the track-info block, the two text-less transport buttons, and the context-menu
 * rows, so UI tests can target them via the semantics click action — which (unlike an injected
 * gesture) isn't defeated by the bottom mini-player overlapping a control, and (unlike matching by
 * text) doesn't collide with the same track title/artist the mini-player shows. Kept in sync with
 * the literals in `NowPlayingTest`.
 */
const val NOW_PLAYING_TRACK_INFO_TAG = "NowPlayingTrackInfo"
const val NOW_PLAYING_MENU_BUTTON_TAG = "NowPlayingMenuButton"
const val NOW_PLAYING_SETLIST_BUTTON_TAG = "NowPlayingSetlistButton"
const val NOW_PLAYING_CTX_BACK_TAG = "NowPlayingCtxBack"
const val NOW_PLAYING_CTX_GAME_TAG = "NowPlayingCtxGame"
const val NOW_PLAYING_CTX_ARTISTS_TAG = "NowPlayingCtxArtists"
const val NOW_PLAYING_CTX_REPEAT_TAG = "NowPlayingCtxRepeat"
const val NOW_PLAYING_CTX_SHUFFLE_TAG = "NowPlayingCtxShuffle"

/** Test tag for the per-artist row in the ARTISTS context menu, keyed by artist id. */
fun nowPlayingCtxArtistTag(artistId: Long) = "NowPlayingCtxArtist:$artistId"
