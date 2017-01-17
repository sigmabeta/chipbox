package net.sigmabeta.chipbox.model.domain


import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import net.sigmabeta.chipbox.model.IdRealmObject

open class Artist() : RealmObject(), IdRealmObject {
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

    companion object {
        val ARTIST_ALL = -1L
    }
}