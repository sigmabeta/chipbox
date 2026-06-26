package net.sigmabeta.chipbox.repository.memory.models

data class MemoryGame(
    val id: Long,
    val title: String,
    val photoUrl: String?,
    val artists: List<MemoryArtist>,
    val tracks: List<MemoryTrack>,
    // Epoch millis the game was added; stamped at insert so the in-memory fake mirrors the real
    // repository's "recently added" behaviour.
    val dateAdded: Long = 0,
)
