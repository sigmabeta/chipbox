package net.sigmabeta.chipbox.model.objects

import android.media.MediaMetadata
import android.support.v4.media.MediaMetadataCompat

data class Track(val id: Long,
                 var trackNumber: Int,
                 val path: String,
                 val title: String,
                 val gameId: Long,
                 val gameTitle: String,
                 val platform: Int,
                 val artist: String,
                 val trackLength: Int,
                 val introLength: Int,
                 val loopLength: Int) {

    companion object {
        val PLATFORM_UNSUPPORTED = 100
        val PLATFORM_ALL = -2
        val PLATFORM_GENESIS = 1
        val PLATFORM_SNES = 2

        fun toMetadata(track: Track): MediaMetadataCompat {
            return MediaMetadataCompat.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, track.gameTitle)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                    .build()
        }
    }
}