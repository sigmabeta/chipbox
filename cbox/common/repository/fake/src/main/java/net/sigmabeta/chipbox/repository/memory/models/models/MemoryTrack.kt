package net.sigmabeta.chipbox.repository.memory.models

data class MemoryTrack(
    val id: Long,
    val path: String,
    val title: String,
    val trackLengthMs: Long,
    val trackNumber: Int,
    val fade: Boolean,
    var game: MemoryGame?,
    val artists: List<MemoryArtist>

)