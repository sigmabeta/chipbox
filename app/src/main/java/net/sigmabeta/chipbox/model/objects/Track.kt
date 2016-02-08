package net.sigmabeta.chipbox.model.objects

import android.database.Cursor
import android.media.MediaMetadata
import android.support.v4.media.MediaMetadataCompat
import net.sigmabeta.chipbox.model.database.*

data class Track(val id: Long,
                 var trackNumber: Int,
                 val path: String,
                 val title: String,
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
        val PLATFORM_GENESIS = 1
        val PLATFORM_SNES = 2

        fun toMetadataBuilder(track: Track): MediaMetadataCompat.Builder {
            return MediaMetadataCompat.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, track.gameTitle)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
        }

        fun fromCursor(toBind: Cursor): Track {
            return Track(
                    toBind.getLong(COLUMN_DB_ID),
                    toBind.getInt(COLUMN_TRACK_NUMBER),
                    toBind.getString(COLUMN_TRACK_PATH),
                    toBind.getString(COLUMN_TRACK_TITLE),
                    toBind.getLong(COLUMN_TRACK_GAME_ID),
                    toBind.getString(COLUMN_TRACK_GAME_TITLE),
                    toBind.getInt(COLUMN_TRACK_GAME_PLATFORM),
                    toBind.getString(COLUMN_TRACK_ARTIST),
                    toBind.getLong(COLUMN_TRACK_LENGTH),
                    toBind.getLong(COLUMN_TRACK_INTRO_LENGTH),
                    toBind.getLong(COLUMN_TRACK_LOOP_LENGTH)
            )
        }
    }
}