package net.sigmabeta.chipbox.model.objects

import android.media.MediaMetadata

data class Track(val id: Long,
                 val trackNumber: Int,
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

        fun toMetadata(track: Track): MediaMetadata {
            return MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, track.gameTitle)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                    .build()
        }
    }
}