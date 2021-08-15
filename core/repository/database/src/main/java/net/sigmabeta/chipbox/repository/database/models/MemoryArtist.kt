package net.sigmabeta.chipbox.repository.database.models

data class DatabaseArtist(
    val id: Long,
    val name: String,
    val photoUrl: String?,
    val tracks: MutableList<DatabaseTrack>,
    val games: MutableList<DatabaseGame>
)