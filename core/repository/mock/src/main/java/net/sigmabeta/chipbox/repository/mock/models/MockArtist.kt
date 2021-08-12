package net.sigmabeta.chipbox.repository.mock.models

data class MockArtist(
    val id: Long,
    val name: String,
    val photoUrl: String?,
    val tracks: List<MockTrack>,
    val games: MutableList<MockGame>
)