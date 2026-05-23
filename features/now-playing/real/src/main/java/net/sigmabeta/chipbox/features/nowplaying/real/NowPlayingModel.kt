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
    val positionMs: Long,
    val lengthMs: Long,
    val canSkipForward: Boolean,
    val isShuffled: Boolean,
    val repeatMode: RepeatMode,
    /**
     * Non-null only for a fatal playback error ([PlayerState.ERROR]). When set, the screen
     * swaps the artwork for its error state with this short message rendered over it, while the
     * surrounding track metadata still describes the track that failed.
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
            positionMs = 0L,
            lengthMs = 0L,
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
