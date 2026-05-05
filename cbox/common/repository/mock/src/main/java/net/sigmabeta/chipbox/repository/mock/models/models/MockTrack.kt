package net.sigmabeta.chipbox.repository.mock.models

data class MockTrack(
    val id: Long,
    val path: String,
    val title: String,
    val trackLengthMs: Long,
    val trackNumber: Int,
    val fade: Boolean,
    var game: MockGame?,
    val artists: List<MockArtist>
)