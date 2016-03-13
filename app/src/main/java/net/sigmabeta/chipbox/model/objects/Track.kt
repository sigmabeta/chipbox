package net.sigmabeta.chipbox.model.objects

import android.media.MediaMetadata
import android.support.v4.media.MediaMetadataCompat

data class Track(val id: Long,
                 var trackNumber: Int,
                 val path: String,
                 var title: String,
                 val gameId: Long,
                 val gameTitle: String,
                 val platform: Int,
                 val artist: String,
                 val trackLength: Long,
                 val introLength: Long,
                 val loopLength: Long) {

    companion object {
        val PLATFORM_UNSUPPORTED = 100
        val PLATFORM_ALL = -2
        val PLATFORM_UNDEFINED = -1
        val PLATFORM_GENESIS = 1
        val PLATFORM_32X = 2
        val PLATFORM_SNES = 3
        val PLATFORM_NES = 4
        val PLATFORM_GAMEBOY = 5

        fun toMetadataBuilder(track: Track): MediaMetadataCompat.Builder {
            return MediaMetadataCompat.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, track.gameTitle)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
        }
    }
}