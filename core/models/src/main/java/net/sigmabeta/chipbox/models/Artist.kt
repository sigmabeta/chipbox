package net.sigmabeta.chipbox.models

data class Artist(
        val id: Long,
        val name: String,
        val tracks: MutableList<Track>?,
        val photoUrl: String?
)