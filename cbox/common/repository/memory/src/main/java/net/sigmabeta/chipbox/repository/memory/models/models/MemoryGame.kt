package net.sigmabeta.chipbox.repository.memory.models

data class MemoryGame(
    val id: Long,
    val title: String,
    val photoUrl: String?,
    val artists: List<MemoryArtist>,
    val tracks: List<MemoryTrack>
)