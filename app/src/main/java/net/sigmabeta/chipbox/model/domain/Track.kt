package net.sigmabeta.chipbox.model.domain

import android.media.MediaMetadata
import android.support.v4.media.MediaMetadataCompat
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import net.sigmabeta.chipbox.model.IdRealmObject
import net.sigmabeta.chipbox.model.domain.ListItem.Companion.CHANGE_ERROR

open class Track() : RealmObject(), IdRealmObject, ListItem {
    constructor(number: Int,
                path: String,
                title: String,
                gameTitle: String,
                artist: String,
                platform: Long,
                trackLength: Long,
                introLength: Long,
                loopLength: Long,
                backendId: Int) : this() {
        this.trackNumber = number
        this.path = path
        this.title = title
        this.gameTitle = gameTitle
        this.artistText = artist
        this.platform = platform
        this.trackLength = trackLength
        this.introLength = introLength
        this.loopLength = loopLength
        this.backendId = backendId
    }

    @PrimaryKey open var id: String? = null

    open var trackNumber: Int? = null
    open var path: String? = null
    open var title: String? = null
    open var platform: Long = -1L
    open var artistText: String? = null
    open var trackLength: Long? = null
    open var introLength: Long? = null
    open var loopLength: Long? = null
    open var game: Game? = null
    open var gameTitle: String? = null
    open var artists: RealmList<Artist>? = null
    open var backendId: Int? = null

    override fun getPrimaryKey() = id
    override fun setPrimaryKey(id: String) {
        this.id = id
    }

    override fun isTheSameAs(theOther: ListItem?): Boolean {
        if (theOther is Track) {
            if (theOther.id == this.id) {
                return true
            }
        }

        return false
    }

    override fun hasSameContentAs(theOther: ListItem?): Boolean {
        if (theOther is Track) {
            if (theOther.artistText == this.artistText) {
                return true
            }
        }

        return false
    }

    override fun getChangeType(theOther: ListItem?): Int {

        if (theOther is Track) {
            if (theOther.artistText != this.artistText) {
                return CHANGE_ARTIST
            }
        }

        return CHANGE_ERROR
    }

    companion object {
        val CHANGE_ARTIST = 1

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
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, track.game?.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artistText)
        }
    }
}