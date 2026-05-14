package net.sigmabeta.chipbox.models

data class Track(
    val id: Long,
    val path: String,
    val source: String,
    val title: String,
    val trackLengthMs: Long,
    val trackNumber: Int,
    val fade: Boolean,
    val game: Game?,
    val artists: List<Artist>?,
    val chainFiles: List<ChainFile> = emptyList(),
)

/**
 * Length of the linear fade-out applied when a track is recorded with [Track.fade] = true.
 * Tracks store [Track.trackLengthMs] *including* this window, so the audible portion ends at
 * `trackLengthMs - FADE_LENGTH_MS` and the fade fills the remainder.
 */
const val FADE_LENGTH_MS: Long = 6_000L