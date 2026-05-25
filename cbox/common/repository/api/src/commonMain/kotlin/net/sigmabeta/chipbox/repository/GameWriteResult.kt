package net.sigmabeta.chipbox.repository

/** Outcome of [Repository.upsertGame]: the affected game's id plus what happened to it. */
data class GameWriteOutcome(
    val gameId: Long,
    val result: GameWriteResult,
)

/** What [Repository.upsertGame] did, so the scanner can raise the matching `ScannerEvent`. */
enum class GameWriteResult {
    /** The game did not exist before and was inserted. */
    ADDED,

    /** The game already existed and something meaningful changed (tracks added/updated/removed,
     *  or its title/photo). */
    UPDATED,

    /** The game already existed and re-scanning produced byte-identical data. */
    UNCHANGED,
}
