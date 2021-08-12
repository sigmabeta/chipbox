package net.sigmabeta.chipbox.models

data class RawGame(
    val title: String,
    val photoUrl: String?,
    val tracks: List<RawTrack>
)