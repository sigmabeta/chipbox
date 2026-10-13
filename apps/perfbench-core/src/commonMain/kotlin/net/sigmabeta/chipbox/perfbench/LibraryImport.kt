package net.sigmabeta.chipbox.perfbench

import net.sigmabeta.chipbox.repository.GameWriteResult
import net.sigmabeta.chipbox.repository.Repository

/**
 * Outcome of [importLibrarySnapshot], counted by what the repository reported per game:
 * [added] new games, [updated] existing games whose content changed, and [unchanged]
 * existing games re-imported identically.
 */
data class LibraryImportResult(
    val added: Int,
    val updated: Int,
    val unchanged: Int,
) {
    val total: Int get() = added + updated + unchanged
}

/**
 * Upsert-merge a [LibrarySnapshot] into this library — the in-app side of the snapshot round-trip
 * (`perfbench snapshot` writes one, this ingests it).
 *
 * Each game is rebased from the snapshot's corpus-relative paths onto [root] and written through
 * the existing `upsertGame` path, so games reconcile by `folderKey` and a game already present is
 * updated in place rather than duplicated. Importing the same snapshot twice is therefore
 * idempotent.
 *
 * [root] is **explicit**: the snapshot's own recorded root is informational, and the caller knows
 * where the corpus actually lives on this machine (a device seed passes its fixed external
 * location; a by-hand desktop import may pass the snapshot's recorded root). The capability never
 * consults the snapshot's root.
 *
 * There is **no file-existence check**: the snapshot's tracked audio need not be present at import
 * time. Playback resolves paths lazily, so the library can be seeded before (or without) the
 * corpus; the caller seeding a benchmark is responsible for the audio being there.
 */
suspend fun Repository.importLibrarySnapshot(
    snapshot: LibrarySnapshot,
    root: String,
): LibraryImportResult {
    var added = 0
    var updated = 0
    var unchanged = 0
    for (game in snapshot.games) {
        when (upsertGame(game.rebased(root)).result) {
            GameWriteResult.ADDED -> added++
            GameWriteResult.UPDATED -> updated++
            GameWriteResult.UNCHANGED -> unchanged++
        }
    }
    return LibraryImportResult(added, updated, unchanged)
}
