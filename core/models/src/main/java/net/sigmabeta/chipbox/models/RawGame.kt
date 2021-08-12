package net.sigmabeta.chipbox.models

data class RawGame(
    val title: String,
    val artists: List<RawArtist>,
    val photoUrl: String?,
    val tracks: List<RawTrack>
)