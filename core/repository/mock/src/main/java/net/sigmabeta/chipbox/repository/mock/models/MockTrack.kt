package net.sigmabeta.chipbox.repository.mock.models

data class MockTrack(
    val id: Long,
    val path: String,
    val title: String,
    val artists: List<MockArtist>,
    var game: MockGame?,
    val trackLengthMs: Long
)