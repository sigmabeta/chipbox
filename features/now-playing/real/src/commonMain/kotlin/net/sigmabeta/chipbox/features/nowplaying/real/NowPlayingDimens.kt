package net.sigmabeta.chipbox.features.nowplaying.real

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

// Shared shape/width/padding for the now-playing panels (the info container, the context menu, and
// the setlist) so the rounded card and the 200..600dp width bounds stay consistent across them.
// (TrackInfo opts out of the width bounds — it wraps its content down to a 256dp floor, no cap.)
internal val NowPlayingPanelMinWidth = 200.dp
internal val NowPlayingPanelMaxWidth = 600.dp
internal val NowPlayingPanelCornerRadius = 16.dp
internal val NowPlayingRowPadding = PaddingValues(horizontal = 8.dp)

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
const val NOW_PLAYING_FAVORITES_BUTTON_TAG = "NowPlayingFavoritesButton"
const val NOW_PLAYING_CTX_BACK_TAG = "NowPlayingCtxBack"
const val NOW_PLAYING_CTX_GAME_TAG = "NowPlayingCtxGame"
const val NOW_PLAYING_CTX_PLATFORM_TAG = "NowPlayingCtxPlatform"
const val NOW_PLAYING_CTX_ARTISTS_TAG = "NowPlayingCtxArtists"
const val NOW_PLAYING_CTX_REPEAT_TAG = "NowPlayingCtxRepeat"
const val NOW_PLAYING_CTX_SHUFFLE_TAG = "NowPlayingCtxShuffle"
const val NOW_PLAYING_CTX_FAVORITES_TAG = "NowPlayingCtxFavorites"
const val NOW_PLAYING_CTX_ADD_PLAYLIST_TAG = "NowPlayingCtxAddPlaylist"
const val NOW_PLAYING_SETLIST_ADD_PLAYLIST_TAG = "NowPlayingSetlistAddPlaylist"

// Read-only file-tag metadata rows in the LINKS context menu.
const val NOW_PLAYING_CTX_JP_TITLE_TAG = "NowPlayingCtxJpTitle"
const val NOW_PLAYING_CTX_JP_ARTIST_TAG = "NowPlayingCtxJpArtist"
const val NOW_PLAYING_CTX_DUMPER_TAG = "NowPlayingCtxDumper"
const val NOW_PLAYING_CTX_DUMP_DATE_TAG = "NowPlayingCtxDumpDate"
const val NOW_PLAYING_CTX_COMMENT_TAG = "NowPlayingCtxComment"

/** Test tag for the per-artist row in the ARTISTS context menu, keyed by artist id. */
fun nowPlayingCtxArtistTag(artistId: Long) = "NowPlayingCtxArtist:$artistId"
