package net.sigmabeta.chipbox.features.nowplaying.real

import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.sage.components.NameCaptionValueListModel
import net.sigmabeta.sage.images.SourceInfo

data class NowPlayingModel(
    val artwork: SourceInfo,
    val sessionTypeLabel: String,
    val sessionSourceName: String,
    val title: String,
    val artistsCaption: String,
    val gameTitle: String,
    val isPlaying: Boolean,
    /**
     * True only while the player is [PlayerState.BUFFERING] — the speaker is silent waiting for
     * the generator to fill buffers. The transport button shows a spinner in place of play/pause.
     */
    val isBuffering: Boolean = false,
    val positionMs: Long,
    val lengthMs: Long,
    /**
     * How much of the active track is already rendered to local cache, in ms. Shown as a
     * subtle secondary fill behind the seek bar so the user can see how much of the track
     * is instantly available. Bounded by [lengthMs]; a cached-file source reports the whole
     * track length here from the first emission.
     */
    val cachedMs: Long = 0L,
    val canSkipForward: Boolean,
    val isShuffled: Boolean,
    val repeatMode: RepeatMode,
    /**
     * Which context menu (if any) replaces the [TrackInfo][NowPlayingContent] block. [ContextMenuMode.NONE]
     * shows the plain track info; the other modes render the in-screen menu of clickable rows.
     */
    val contextMenuMode: ContextMenuMode = ContextMenuMode.NONE,
    /**
     * When true, the reorderable [setlist] replaces the whole [InfoContainer][NowPlayingContent]
     * block (a peer swap, like the menu swaps TrackInfo↔ContextMenu *within* it). Mutually
     * exclusive with an open [contextMenuMode].
     */
    val setlistVisible: Boolean = false,
    /**
     * The current playback setlist as reorderable rows (in queue order), shown when
     * [setlistVisible]. Each row's `dataId` is its stable queue slot id (not the track id, so a
     * duplicated track stays distinct) and `active` marks the playing slot; the row click jumps
     * playback, the handle reorders.
     */
    val setlist: List<NameCaptionValueListModel> = emptyList(),
    /** The playing track's game id, used by the LINKS game row to navigate to game detail. */
    val gameId: Long = 0L,
    /** Human-readable platform name for the LINKS platform row, e.g. "SNES". Empty when unknown. */
    val platformLabel: String = "",
    /** The playing track's artists (id + name), backing the LINKS artist row and the ARTISTS list. */
    val artists: List<NowPlayingArtist> = emptyList(),
    /** Human-readable repeat state for the CONTROLS row, e.g. "Repeating one track". */
    val repeatStatusLabel: String = "",
    /** Human-readable shuffle state for the CONTROLS row, e.g. "Playing in order". */
    val shuffleStatusLabel: String = "",
    /** Label for the CONTROLS favorite row — "Add to Favorites" or "Remove from Favorites". */
    val favoriteLabel: String = "",
    /** Whether the playing track is favorited, driving the CONTROLS row's filled/empty heart. */
    val isTrackFavorite: Boolean = false,
    /** Label for the CONTROLS "add the current track to a playlist" row. */
    val addToPlaylistLabel: String = "",
    /** Label for the setlist pane's "add the whole setlist to a playlist" row. */
    val setlistAddToPlaylistLabel: String = "",
    /**
     * Whether the setlist pane shows its "Add to Playlist" row: true only when the setlist has tracks
     * and isn't the whole library (the All Tracks session has no meaningful finite list to capture).
     */
    val canAddSetlistToPlaylist: Boolean = false,
    /**
     * Non-null only for a fatal playback error ([PlayerState.ERROR]). When set, the transport
     * play/pause button switches to a warning icon; the error detail itself is surfaced in the
     * [errors] log shown below the artwork.
     */
    val errorMessage: String? = null,
    /**
     * Rolling log of the most recent playback errors (newest last), shown in the error section
     * below the artwork. Empty when there's nothing to report or after the section auto-clears.
     */
    val errors: List<NowPlayingError> = emptyList(),
) {
    companion object {
        val Empty = NowPlayingModel(
            artwork = SourceInfo(info = null),
            sessionTypeLabel = "",
            sessionSourceName = "",
            title = "",
            artistsCaption = "",
            gameTitle = "",
            isPlaying = false,
            isBuffering = false,
            positionMs = 0L,
            lengthMs = 0L,
            cachedMs = 0L,
            canSkipForward = false,
            isShuffled = false,
            repeatMode = RepeatMode.OFF,
            errorMessage = null,
            errors = emptyList(),
        )
    }
}

/**
 * A single entry in the now-playing error log. [id] is a stable, monotonic identifier assigned
 * when the error is recorded so Compose can key rows and the user can dismiss one individually.
 */
data class NowPlayingError(
    val id: Long,
    val message: String,
)

/**
 * The in-screen "ContextMenu" that replaces the track-info block when active.
 *
 * - [NONE]: no menu — the plain track-info text is shown.
 * - [LINKS]: jump-off links for the current track — its game and artist(s).
 * - [ARTISTS]: one row per artist, shown when a multi-artist track's artist link is tapped.
 * - [CONTROLS]: current repeat & shuffle state as human-readable rows that toggle on tap.
 */
enum class ContextMenuMode { NONE, LINKS, ARTISTS, CONTROLS }

/** A single artist entry backing the LINKS artist row and the ARTISTS list rows. */
data class NowPlayingArtist(
    val id: Long,
    val name: String,
)
