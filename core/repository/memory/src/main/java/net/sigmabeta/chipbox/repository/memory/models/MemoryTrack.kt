package net.sigmabeta.chipbox.repository.memory.models

data class MemoryTrack(
    val id: Long,
    val path: String,
    val title: String,
    val artists: List<MemoryArtist>,
    val fade: Boolean,
    var game: MemoryGame?,
    val trackLengthMs: Long

)