package net.sigmabeta.chipbox.coverart

/** What happened when fetching cover art for one game. */
enum class CoverArtOutcome { DOWNLOADED, UP_TO_DATE, NO_MATCH, NO_COVER, NO_FOLDER, FAILED }

/**
 * Per-game cover-art result; [detail] is the saved file name (on success) or an error message.
 * [fromCache] is true when the lookup came from the persistent cache instead of the IGDB API.
 */
data class CoverArtResult(
    val title: String,
    val outcome: CoverArtOutcome,
    val detail: String? = null,
    val fromCache: Boolean = false,
)

/** Aggregate counts across a run (so far). [fromCache] cross-cuts the outcomes, so it's not in [total]. */
data class CoverArtSummary(
    val downloaded: Int,
    val upToDate: Int,
    val noMatch: Int,
    val noCover: Int,
    val skipped: Int,
    val failed: Int,
    val fromCache: Int,
) {
    val total: Int get() = downloaded + upToDate + noMatch + noCover + skipped + failed
}
