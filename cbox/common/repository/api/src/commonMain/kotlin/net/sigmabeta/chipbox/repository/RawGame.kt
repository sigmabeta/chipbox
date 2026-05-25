package net.sigmabeta.chipbox.repository

data class RawGame(
    val title: String,
    val photoUrl: String?,
    // Stable identity of the source folder this game was scanned from (the SAF parent-document id
    // on Android, the directory path on the JVM). Used to reconcile games across rescans so a
    // folder maps to the same game row even if its title metadata changes.
    val folderKey: String,
    // Hash of the folder's files (path + size + last-modified) at scan time; persisted so the next
    // scan can skip this folder if it recomputes to the same value.
    val folderSignature: String,
    val tracks: List<RawTrack>
)
