package net.sigmabeta.chipbox.model.domain

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import net.sigmabeta.chipbox.model.IdRealmObject
import net.sigmabeta.chipbox.model.domain.ListItem.Companion.CHANGE_ERROR

open class Platform() : RealmObject(), IdRealmObject, ListItem {
    constructor(name: String) : this() {
        this.name = name;
    }

    @PrimaryKey open var id: String? = null

    open var name: String? = null

    override fun getPrimaryKey() = id
    override fun setPrimaryKey(id: String) {
        this.id = id
    }

    override fun isTheSameAs(theOther: ListItem?): Boolean {
        if (theOther is Platform) {
            if (theOther.id == this.id) {
                return true
            }
        }

        return false
    }

    override fun hasSameContentAs(theOther: ListItem?): Boolean {
        if (theOther is Platform) {
            if (theOther.id == this.id) {
                return true
            }
        }

        return false
    }

    override fun getChangeType(theOther: ListItem?): Int {
        return CHANGE_ERROR
    }
}