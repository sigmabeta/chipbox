package net.sigmabeta.chipbox.repository.memory.models

data class MemoryArtist(
    val id: Long,
    val name: String,
    val photoUrl: String?,
    val tracks: MutableList<MemoryTrack>,
    val games: MutableList<MemoryGame>
)