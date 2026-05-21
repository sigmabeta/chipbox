package net.sigmabeta.chipbox.features.nowplaying.real

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.models.Track
import net.sigmabeta.chipbox.player.common.Session
import net.sigmabeta.chipbox.player.common.SessionType
import net.sigmabeta.chipbox.player.director.ChipboxPlaybackState
import net.sigmabeta.chipbox.player.director.PlayerState
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.sage.components.TitleBarModel
import net.sigmabeta.sage.freeform.FreeformState
import net.sigmabeta.sage.images.SourceInfo
import net.sigmabeta.sage.ui.StringProvider

data class NowPlayingState(
    val track: Track? = null,
    val playback: ChipboxPlaybackState? = null,
    val session: Session? = null,
    val repeatMode: RepeatMode = RepeatMode.OFF,
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
        positionMs = playback?.position ?: 0L,
        lengthMs = track?.trackLengthMs ?: 0L,
        canSkipForward = playback?.skipForwardAllowed == true,
        isShuffled = session?.shuffled == true,
        repeatMode = repeatMode,
    )

    /**
     * First line of the now-playing header — describes the *kind* of session, e.g.
     * "Playing from game" or "Shuffling all tracks". The verb tracks `session.shuffled`;
     * the rest comes from `session.type`. Empty when there is no session yet so the screen
     * can hide the header entirely instead of showing a misleading placeholder.
     */
    private fun sessionTypeLabel(stringProvider: StringProvider): String {
        val session = session ?: return ""
        val shuffled = session.shuffled
        return stringProvider.getString(
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
            }
        )
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
        }
    }

    override fun errorContent(error: Throwable): NowPlayingModel = NowPlayingModel.Empty

    // Mirrors PlayerStatusViewModel.isPlaying() so the play/pause icon agrees with the mini-bar.
    private fun PlayerState.isPlaying(): Boolean = when (this) {
        PlayerState.PLAYING,
        PlayerState.PRELOADING,
        PlayerState.BUFFERING,
        PlayerState.FAST_FORWARDING,
        PlayerState.REWINDING,
        PlayerState.ENDING -> true

        PlayerState.PAUSED,
        PlayerState.ERROR,
        PlayerState.IDLE,
        PlayerState.STOPPED -> false
    }
}
