package net.sigmabeta.chipbox.model.domain


import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import net.sigmabeta.chipbox.model.IdRealmObject
import net.sigmabeta.chipbox.model.domain.ListItem.Companion.CHANGE_ERROR

open class Artist() : RealmObject(), IdRealmObject, ListItem {
    constructor(name: String) : this() {
        this.name = name
    }

    @PrimaryKey open var id: String? = null

    open var name: String? = null
    open var tracks: RealmList<Track>? = null

    override fun getPrimaryKey() = id
    override fun setPrimaryKey(id: String) {
        this.id = id
    }

    override fun isTheSameAs(theOther: ListItem?): Boolean {
        if (theOther is Artist) {
            if (theOther.id == this.id) {
                return true
            }
        }

        return false
    }

    override fun hasSameContentAs(theOther: ListItem?): Boolean {
        if (theOther is Artist) {
            if (theOther.tracks?.size == this.tracks?.size) {
                return true
            }
        }

        return false
    }

    override fun getChangeType(theOther: ListItem?): Int {
        if (theOther is Artist) {
            if (theOther.tracks?.size != this.tracks?.size) {
                return CHANGE_TRACK_COUNT
            }
        }

        return CHANGE_ERROR
    }

    companion object {
        val ARTIST_ALL = -1L

        val CHANGE_TRACK_COUNT = 1
    }
}