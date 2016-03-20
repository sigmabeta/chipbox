package net.sigmabeta.chipbox.model.domain

import android.media.MediaMetadata
import android.support.v4.media.MediaMetadataCompat
import com.raizlabs.android.dbflow.annotation.PrimaryKey
import com.raizlabs.android.dbflow.annotation.Table
import com.raizlabs.android.dbflow.structure.BaseModel
import net.sigmabeta.chipbox.ChipboxDatabase

@Table(database = ChipboxDatabase::class, allFields = true)
class Track() : BaseModel() {
    constructor(number: Int,
                path: String,
                title: String,
                gameTitle: String,
                artist: String,
                platform: Long,
                trackLength: Long,
                introLength: Long,
                loopLength: Long) : this() {
        this.trackNumber = number
        this.path = path
        this.title = title
        this.gameTitle = gameTitle
        this.artist = artist
        this.platform = platform
        this.trackLength = trackLength
        this.introLength = introLength
        this.loopLength = loopLength
    }

    @PrimaryKey (autoincrement = true) var id: Long? = null
    var trackNumber: Int? = null
    var path: String? = null
    var title: String? = null
    var gameId: Long? = null
    var gameTitle: String? = null
    var platform: Long? = null
    var artistId: Long? = null
    var artist: String? = null
    var trackLength: Long? = null
    var introLength: Long? = null
    var loopLength: Long? = null

    companion object {
        val PLATFORM_UNSUPPORTED = 100L
        val PLATFORM_ALL = -2L
        val PLATFORM_UNDEFINED = -1L
        val PLATFORM_GENESIS = 1L
        val PLATFORM_32X = 2L
        val PLATFORM_SNES = 3L
        val PLATFORM_NES = 4L
        val PLATFORM_GAMEBOY = 5L

        fun toMetadataBuilder(track: Track): MediaMetadataCompat.Builder {
            return MediaMetadataCompat.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, track.gameTitle)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
        }
    }
}