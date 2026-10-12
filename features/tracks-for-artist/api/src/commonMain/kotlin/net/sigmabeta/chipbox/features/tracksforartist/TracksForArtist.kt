package net.sigmabeta.chipbox.features.tracksforartist

import kotlinx.serialization.Serializable

@Serializable
data class TracksForArtist(val artistId: Long, val artistName: String)
