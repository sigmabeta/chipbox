package net.sigmabeta.chipbox.models

data class Track(
    val id: Long,
    val path: String,
    val source: String,
    val title: String,
    val trackLengthMs: Long,
    val trackNumber: Int,
    val fadeLengthMs: Long,
    val game: Game?,
    val artists: List<Artist>?,
    val chainFiles: List<ChainFile> = emptyList(),
)

/**
 * Default fade-out duration used when a reader determines a track should fade but provides no
 * explicit duration (e.g. NSF/GBS, which have no fade metadata). The fade plays *after* the
 * musical end at `[trackLengthMs, trackLengthMs + fadeLengthMs]`; tracks with `fadeLengthMs = 0`
 * get no fade at all and end at `trackLengthMs`.
 */
const val FADE_LENGTH_MS: Long = 6_000L