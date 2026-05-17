package net.sigmabeta.chipbox.repository.memory.models

data class MemoryTrack(
    val id: Long,
    val path: String,
    val title: String,
    val trackLengthMs: Long,
    val trackNumber: Int,
    val fadeLengthMs: Long,
    var game: MemoryGame?,
    val artists: List<MemoryArtist>

)
