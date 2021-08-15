package net.sigmabeta.chipbox.repository

data class RawGame(
    val title: String,
    val photoUrl: String?,
    val tracks: List<RawTrack>
)