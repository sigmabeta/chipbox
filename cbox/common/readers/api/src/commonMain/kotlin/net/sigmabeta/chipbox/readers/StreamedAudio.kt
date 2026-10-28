package net.sigmabeta.chipbox.readers

import net.sigmabeta.chipbox.models.Platform
import net.sigmabeta.chipbox.repository.RawTrack
import net.sigmabeta.sage.logging.Hatchet

/**
 * 1-based track number for the streamed-audio subsong at [subsongIndex] (0-based).
 *
 * **Streamed readers number subsongs 1-based; this is the deliberate exception to the chiptune
 * readers' 0-based convention.** Both invariants are load-bearing:
 * - Playback forwards `RawTrack.trackNumber` straight to the emulator's `setTrackNumber`, whose
 *   native vgmstream side treats the argument as a subsong number and clamps it with
 *   `subsong > 0 ? subsong : 1` — a 0-based streamed number would reopen the first subsong.
 * - The database identifies a track by `(path, trackNumber)`, and every streamed track persisted
 *   before the Kotlin readers existed was written by the native probe with `subsong = index + 1`.
 *   Keeping that convention keeps rescan identities stable.
 *
 * The chiptune readers (`NsfReader`, `GbsReader`, `RsnReader`, …) emit raw 0-based subtune indices;
 * only streamed readers call this helper.
 */
fun subsongTrackNumber(subsongIndex: Int): Int = subsongIndex + 1

/**
 * Best-effort platform for a streamed-audio file, inferred from its [extension].
 *
 * vgmstream's native probe reports sample rate, length and stream name but not the source console,
 * so files routed through it would otherwise all land in [Platform.OTHER]. These extensions are
 * unambiguous PlayStation formats in vgmstream's format set (CD-XA audio, PS-ADPCM VAG, VAB sound
 * banks, SEQ sequences), so map them to [Platform.PSX]; anything else stays [Platform.OTHER]. This
 * is metadata only — it never affects which decoder plays the file.
 *
 * A dedicated reader that knows its own console should set `RawTrack.platform` itself; this helper
 * is the fallback for extensions the readers do not map.
 */
fun platformForStreamedExtension(extension: String): Platform = when (extension.lowercase()) {
    "xa", "vag", "vb", "vh", "vab", "seq", "vpk" -> Platform.PSX
    else -> Platform.OTHER
}

/** Loop passes the native probe plays for a looping stream (`loop_count = 2.0`). */
private const val STREAMED_LOOP_COUNT = 2.0

/** Fade seconds the native probe appends to a looping stream (`fade_time = 10.0`). */
private const val STREAMED_FADE_SECONDS = 10.0

private const val MILLIS_PER_SECOND = 1_000L

/**
 * Length in whole milliseconds of a streamed track, matching the native probe exactly.
 *
 * Mirrors `base/play_state.c` and the probe's fixed config (`loop_count = 2.0`,
 * `fade_time = 10.0`, no other trims): a looping stream plays
 * `loop_start + (loop_end - loop_start) * 2 + 10 * sample_rate` samples — the intro, two loop
 * passes and a ten-second fade — while a non-looping stream plays its sample count. The native
 * side accumulates that sum as a `double` and truncates it to `play_samples`, then reports
 * `play_samples * 1000 / sample_rate` with integer (truncating) division; this does the same with
 * [Long] arithmetic, so do not round.
 */
internal fun streamedLengthMs(
    numSamples: Long,
    loopFlag: Boolean,
    loopStartSample: Long,
    loopEndSample: Long,
    sampleRate: Int,
): Long {
    if (sampleRate <= 0) return 0L
    val playSamples = if (loopFlag) {
        (
            loopStartSample + (loopEndSample - loopStartSample) * STREAMED_LOOP_COUNT +
            STREAMED_FADE_SECONDS * sampleRate
        ).toLong()
    } else {
        numSamples
    }
    return playSamples.coerceAtLeast(0L) * MILLIS_PER_SECOND / sampleRate
}

/**
 * Runs a streamed reader's header parse and maps the two exceptions a [ByteReader] read can throw
 * onto the module's miss convention: log a warning and return `null` (a parse failure), never
 * propagate. Every streamed reader with a simple try-parse-catch shape uses this so the failure
 * contract lives in one place.
 *
 * [label] names the format in the log line. [parse] returns the tracks, `null` for a malformed
 * header, or `emptyList()` for a file that is recognised but has no stream this build can decode —
 * that distinction is the caller's, not this wrapper's.
 */
internal inline fun readStreamedTracks(
    label: String,
    identifier: String,
    hatchet: Hatchet,
    parse: () -> List<RawTrack>?,
): List<RawTrack>? = try {
    parse()
} catch (e: IndexOutOfBoundsException) {
    hatchet.w("$label parse failed: truncated header for $identifier (${e.message}).")
    null
} catch (e: IllegalArgumentException) {
    hatchet.w("$label parse failed: illegal argument for $identifier — ${e.message}")
    null
}

/**
 * Builds the single [RawTrack] a streamed reader yields for the audio stream at [subsongIndex]
 * (0-based; stored 1-based, see [subsongTrackNumber]).
 *
 * Every streamed reader shares this shape so the scanner-visible contract stays in one place:
 * - [title] is the container/stream name when the format carries one, else [TAG_UNKNOWN], which
 *   lets the scanner's single-track filename fallback produce the same titles as the native probe.
 * - `artist`/`game` are always [TAG_UNKNOWN]; the scanner fills `game` from the folder name.
 * - `fadeLengthMs` is always `0` — the probe's fade is already inside [lengthMs], and the scanner's
 *   zero-fade path then appends its 2 000 ms padding, matching the stored database lengths.
 * - [platform] is [Platform.OTHER] unless the reader's format implies a console — pass it
 *   explicitly rather than deriving it from [identifier]. A reader must never parse the path:
 *   identifiers are opaque content-source ids (a SAF URI carries no `.<ext>` suffix), so the
 *   format's own constant is the only sound source of platform truth.
 */
internal fun streamedRawTrack(
    identifier: String,
    lengthMs: Long,
    title: String = TAG_UNKNOWN,
    subsongIndex: Int = 0,
    platform: Platform = Platform.OTHER,
): RawTrack = RawTrack(
    path = identifier,
    source = "",
    title = title,
    artist = TAG_UNKNOWN,
    game = TAG_UNKNOWN,
    length = lengthMs,
    trackNumber = subsongTrackNumber(subsongIndex),
    fadeLengthMs = 0L,
    platform = platform,
)
