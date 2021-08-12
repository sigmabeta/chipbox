package net.sigmabeta.chipbox.repository.mock.models

data class MockGame(
    val id: Long,
    val title: String,
    val photoUrl: String?,
    val artists: List<MockArtist>,
    val tracks: List<MockTrack>
)