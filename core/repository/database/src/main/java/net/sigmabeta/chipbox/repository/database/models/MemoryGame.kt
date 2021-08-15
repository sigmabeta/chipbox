package net.sigmabeta.chipbox.repository.database.models

data class DatabaseGame(
    val id: Long,
    val title: String,
    val photoUrl: String?,
    val artists: List<DatabaseArtist>,
    val tracks: List<DatabaseTrack>
)