package net.sigmabeta.chipbox.models

import kotlinx.serialization.Serializable

@Serializable
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
    val extension: String = "",
    val platform: Platform,
    // The owning game's id, always known (a track is never gameless — `track.game_id` is a non-null
    // FK). Unlike [game], it needs no hydration, so callers can group/identify a track's game
    // without paying for a `withGame` join. Defaults from [game] for convenience constructions;
    // the repository converters set it explicitly so it's correct even when [game] is not hydrated.
    // `0` means "unset" (no real game row has id 0).
    val gameId: Long = game?.id ?: 0L,
    // Optional descriptive metadata pulled from the file's tags, null when it carried none.
    val comment: String? = null,
    val dumper: String? = null,
    val dumpDate: String? = null,
    val titleJp: String? = null,
    val artistJp: String? = null,
)

/**
 * Default fade-out duration used when a reader determines a track should fade but provides no
 * explicit duration (e.g. NSF/GBS, which have no fade metadata). The fade plays *after* the
 * musical end at `[trackLengthMs, trackLengthMs + fadeLengthMs]`; tracks with `fadeLengthMs = 0`
 * get no fade at all and end at `trackLengthMs`.
 */
const val FADE_LENGTH_MS: Long = 6_000L
