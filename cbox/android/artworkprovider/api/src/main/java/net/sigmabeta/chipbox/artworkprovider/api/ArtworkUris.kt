package net.sigmabeta.chipbox.artworkprovider.api

import android.net.Uri

object ArtworkUris {
    const val AUTHORITY = "net.sigmabeta.chipbox.artworkprovider.api"

    const val SEGMENT_GAME = "game"
    const val SEGMENT_ARTIST = "artist"

    fun forGame(id: Long): Uri = Uri.parse("content://$AUTHORITY/$SEGMENT_GAME/$id")

    fun forArtist(id: Long): Uri = Uri.parse("content://$AUTHORITY/$SEGMENT_ARTIST/$id")
}
