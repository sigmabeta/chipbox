package net.sigmabeta.chipbox.model.domain


import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import net.sigmabeta.chipbox.model.IdRealmObject
import net.sigmabeta.chipbox.model.domain.ListItem.Companion.CHANGE_ERROR

open class Game() : RealmObject(), IdRealmObject, ListItem {
    constructor(title: String, platform: Long) : this() {
        this.title = title
        this.platform = platform
    }

    @PrimaryKey open var id: String? = null

    open var title: String? = null
    open var platform: Long? = null

    open var artLocal: String? = null
    open var artWeb: String? = null
    open var company: String? = null
    open var multipleArtists: Boolean? = null
    open var artist: Artist? = null
        get() {
            if (multipleArtists ?: false) {
                return Artist("Various Artists")
            } else {
                return field
            }
        }

    open var tracks: RealmList<Track>? = null

    override fun getPrimaryKey() = id
    override fun setPrimaryKey(id: String) {
        this.id = id
    }


    override fun isTheSameAs(theOther: ListItem?): Boolean {
        if (theOther is Game) {
            if (theOther.id == this.id) {
                return true
            }
        }

        return false
    }

    override fun hasSameContentAs(theOther: ListItem?): Boolean {
        if (theOther is Game) {
            if (this.artist?.name == theOther.artist?.name) {
                return true
            }
        }

        return false
    }

    override fun getChangeType(theOther: ListItem?): Int {
        if (theOther is Game) {
            if (this.artist?.name != theOther.artist?.name) {
                return CHANGE_ARTIST
            }
        }

        return CHANGE_ERROR
    }

    companion object {
        val PICASSO_PREFIX = "file://"

        val ASSET_ALBUM_ART_BLANK = "/android_asset/img_album_art_blank.png"

        val PICASSO_ASSET_ALBUM_ART_BLANK = PICASSO_PREFIX + ASSET_ALBUM_ART_BLANK

        val CHANGE_ARTIST = 1
    }
}