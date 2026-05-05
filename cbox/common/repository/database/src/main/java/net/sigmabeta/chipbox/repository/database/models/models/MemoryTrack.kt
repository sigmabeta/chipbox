package net.sigmabeta.chipbox.repository.database.models

data class DatabaseTrack(
    val id: Long,
    val path: String,
    val title: String,
    val artists: List<DatabaseArtist>,
    var game: DatabaseGame?,
    val trackLengthMs: Long

)