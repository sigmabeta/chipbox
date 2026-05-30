package net.sigmabeta.chipbox.features.nowplaying.real

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
