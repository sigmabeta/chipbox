package net.sigmabeta.chipbox.model.domain


import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class Artist() : RealmObject() {
    constructor(name: String) : this() {
        this.name = name
    }

    @PrimaryKey var id: Long? = null

    var name: String? = null
    var tracks: RealmList<Track>? = null

    companion object {
        val ARTIST_ALL = -1L
    }
}