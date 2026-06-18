package net.sigmabeta.chipbox.features.nowplaying.real

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.RepeatMode
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.components.NameCaptionValueListModel
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.freeform.FreeformState
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.StringProvider

data class NowPlayingState(
    val track: Track? = null,
    val playback: ChipboxPlaybackState? = null,
    val session: Session? = null,
    val errors: List<NowPlayingError> = emptyList(),
    val contextMenuMode: ContextMenuMode = ContextMenuMode.NONE,
    /** When true, the reorderable setlist replaces the InfoContainer block. */
    val setlistVisible: Boolean = false,
    /** The current playback setlist resolved to track metadata, in queue order. */
    val setlistTracks: List<Track> = emptyList(),
) : FreeformState<NowPlayingModel>() {

    override fun title(stringProvider: StringProvider) = TitleBarModel(
        title = stringProvider.getString(ChipboxStringId.NOW_PLAYING_SCREEN_TITLE),
        shouldShowBack = true,
    )

    override fun toContent(stringProvider: StringProvider): NowPlayingModel = NowPlayingModel(
        artwork = SourceInfo(info = track?.game?.photoUrl),
        sessionTypeLabel = sessionTypeLabel(stringProvider),
        sessionSourceName = sessionSourceName(stringProvider),
        title = track?.title.orEmpty(),
        artistsCaption = track?.artists?.joinToString(", ") { it.name }.orEmpty(),
        gameTitle = track?.game?.title.orEmpty(),
        isPlaying = playback?.state?.isPlaying() == true,
        isBuffering = playback?.state == PlayerState.BUFFERING,
        positionMs = playback?.position ?: 0L,
        lengthMs = track?.trackLengthMs ?: 0L,
        cachedMs = playback?.cachedMs ?: 0L,
        canSkipForward = playback?.skipForwardAllowed == true,
        isShuffled = session?.shuffled == true,
        repeatMode = session?.repeatMode ?: RepeatMode.OFF,
        contextMenuMode = contextMenuMode,
        setlistVisible = setlistVisible,
        setlist = setlistRows(),
        gameId = track?.gameId ?: 0L,
        artists = track?.artists?.map { NowPlayingArtist(id = it.id, name = it.name) }.orEmpty(),
        repeatStatusLabel = stringProvider.getString(repeatStatusStringId()),
        shuffleStatusLabel = stringProvider.getString(shuffleStatusStringId()),
        // Only a fatal ERROR carries a message; its presence drives the transport warning icon.
        errorMessage = playback
            ?.takeIf { it.state == PlayerState.ERROR }
            ?.errorMessage,
        errors = errors,
    )

    /**
     * The setlist queue as reorderable rows. The active row is the currently-playing track. Built
     * as plain [NameCaptionValueListModel]s so the inline reorderable list can render them with
     * [net.sigmabeta.chipbox.common.ui.components.api.NameCaptionValueListItem] — the same row the
     * standalone setlist screen uses.
     */
    private fun setlistRows(): List<NameCaptionValueListModel> = setlistTracks.map { track ->
        NameCaptionValueListModel(
            dataId = track.id,
            name = track.title,
            caption = track.game?.title.orEmpty(),
            value = formatTrackLength(track.trackLengthMs),
            clickAction = NowPlayingAction.SetlistTrackClicked(track.id),
            active = track.id == this.track?.id,
        )
    }

    private fun formatTrackLength(millis: Long): String {
        val totalSeconds = millis / MS_PER_SECOND
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    /**
     * First line of the now-playing header — describes the *kind* of session, e.g.
     * "Playing from game" or "Shuffling all tracks". The verb tracks `session.shuffled`;
     * the rest comes from `session.type`. Empty when there is no session yet so the screen
     * can hide the header entirely instead of showing a misleading placeholder.
     */
    private fun sessionTypeLabel(stringProvider: StringProvider): String {
        val session = session ?: return ""
        val shuffled = session.shuffled
        val base = stringProvider.getString(
            when (session.type) {
                SessionType.GAME ->
                    if (shuffled) {
                        ChipboxStringId.NOW_PLAYING_SESSION_TYPE_GAME_SHUFFLING
                    } else {
                        ChipboxStringId.NOW_PLAYING_SESSION_TYPE_GAME_PLAYING
                    }

                SessionType.ARTIST ->
                    if (shuffled) {
                        ChipboxStringId.NOW_PLAYING_SESSION_TYPE_ARTIST_SHUFFLING
                    } else {
                        ChipboxStringId.NOW_PLAYING_SESSION_TYPE_ARTIST_PLAYING
                    }

                SessionType.PLAYLIST ->
                    if (shuffled) {
                        ChipboxStringId.NOW_PLAYING_SESSION_TYPE_PLAYLIST_SHUFFLING
                    } else {
                        ChipboxStringId.NOW_PLAYING_SESSION_TYPE_PLAYLIST_PLAYING
                    }

                SessionType.ALL_TRACKS ->
                    if (shuffled) {
                        ChipboxStringId.NOW_PLAYING_SESSION_TYPE_ALL_TRACKS_SHUFFLING
                    } else {
                        ChipboxStringId.NOW_PLAYING_SESSION_TYPE_ALL_TRACKS_PLAYING
                    }

                SessionType.PLATFORM ->
                    if (shuffled) {
                        ChipboxStringId.NOW_PLAYING_SESSION_TYPE_PLATFORM_SHUFFLING
                    } else {
                        ChipboxStringId.NOW_PLAYING_SESSION_TYPE_PLATFORM_PLAYING
                    }

                SessionType.SETLIST ->
                    if (shuffled) {
                        ChipboxStringId.NOW_PLAYING_SESSION_TYPE_SETLIST_SHUFFLING
                    } else {
                        ChipboxStringId.NOW_PLAYING_SESSION_TYPE_SETLIST_PLAYING
                    }

                // A one-off random pick — no shuffle distinction (the setlist has one entry).
                SessionType.SINGLE_TRACK ->
                    ChipboxStringId.NOW_PLAYING_SESSION_TYPE_SINGLE_TRACK_PLAYING
            }
        )
        // A user-edited setlist (reorder/remove) gets a "(Modified)" prefix on the type label.
        return if (session.modified) {
            "${stringProvider.getString(ChipboxStringId.NOW_PLAYING_LABEL_MODIFIED_PREFIX)} $base"
        } else {
            base
        }
    }

    /**
     * Second line of the header — the source's display name (e.g. "Street Fighter II",
     * "Yoko Shimomura", "SNES"). Empty for ALL_TRACKS (no source) and for sources that
     * aren't yet plumbed through (playlists), so the screen can skip rendering the
     * second line.
     */
    private fun sessionSourceName(stringProvider: StringProvider): String {
        val session = session ?: return ""
        return when (session.type) {
            SessionType.GAME -> track?.game?.title.orEmpty()

            // Tracks can have multiple artists; the session points at one specifically via
            // contentId, so prefer that match and fall back to the first listed artist
            // (e.g. mid-load before joined artists are populated).
            SessionType.ARTIST -> {
                val artists = track?.artists.orEmpty()
                artists.firstOrNull { it.id == session.contentId }?.name
                    ?: artists.firstOrNull()?.name
                    ?: ""
            }

            // Playlists aren't wired up yet — no source name to surface.
            SessionType.PLAYLIST -> ""

            SessionType.ALL_TRACKS -> ""

            // Ad-hoc queue (e.g. search results) — the caller-supplied label is the source
            // name (the search query); no backing collection to derive one from.
            SessionType.SETLIST -> session.sourceName.orEmpty()

            // contentId carries the Platform ordinal (see SessionType docs).
            SessionType.PLATFORM ->
                Platform.entries.getOrNull(session.contentId.toInt())
                    ?.let { stringProvider.getString(it.stringId) }
                    ?: ""

            // No backing source — just one track, label-only header.
            SessionType.SINGLE_TRACK -> ""
        }
    }

    /** Maps the current repeat mode to its human-readable CONTROLS-row label. */
    private fun repeatStatusStringId(): ChipboxStringId = when (session?.repeatMode ?: RepeatMode.OFF) {
        RepeatMode.OFF -> ChipboxStringId.NOW_PLAYING_CONTROLS_REPEAT_OFF
        RepeatMode.ALL -> ChipboxStringId.NOW_PLAYING_CONTROLS_REPEAT_ALL
        RepeatMode.ONE -> ChipboxStringId.NOW_PLAYING_CONTROLS_REPEAT_ONE
    }

    /** Maps the current shuffle state to its human-readable CONTROLS-row label. */
    private fun shuffleStatusStringId(): ChipboxStringId =
        if (session?.shuffled == true) {
            ChipboxStringId.NOW_PLAYING_CONTROLS_SHUFFLE_ON
        } else {
            ChipboxStringId.NOW_PLAYING_CONTROLS_SHUFFLE_OFF
        }

    override fun errorContent(error: Throwable): NowPlayingModel = NowPlayingModel.Empty

    // Mirrors PlayerStatusViewModel.isPlaying() so the play/pause icon agrees with the mini-bar.
    private fun PlayerState.isPlaying(): Boolean = when (this) {
        PlayerState.PLAYING,
        PlayerState.BUFFERING,
        PlayerState.ENDING -> true

        PlayerState.PAUSED,
        PlayerState.ERROR,
        PlayerState.IDLE,
        PlayerState.STOPPED -> false
    }

    private companion object {
        const val MS_PER_SECOND = 1_000L
        const val SECONDS_PER_MINUTE = 60L
    }
}
