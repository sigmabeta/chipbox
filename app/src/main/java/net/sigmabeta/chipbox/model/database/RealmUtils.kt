package net.sigmabeta.chipbox.model.database

import io.realm.Realm
import io.realm.RealmObject
import io.realm.internal.RealmObjectProxy
import net.sigmabeta.chipbox.model.IdRealmObject
import java.util.*

inline fun <reified T : RealmObject> T.getNextPrimaryKey(): String {
    val realm = Realm.getDefaultInstance()

    val id = UUID.randomUUID()

    return id.toString()
}

inline fun <reified T : RealmObject> T.save(): T {
    val realm = Realm.getDefaultInstance()

    val wasInTransactionBefore: Boolean
    if (!realm.isInTransaction) {
        wasInTransactionBefore = true
        realm.beginTransaction()
    } else {
        wasInTransactionBefore = false
    }

    if (this is IdRealmObject) {
        if (this.getPrimaryKey() == null) {
            this.setPrimaryKey(getNextPrimaryKey())
        }
    }

    val managedObject = realm.copyToRealm(this)

    if (wasInTransactionBefore) {
        realm.commitTransaction()
    }

    realm.close()

    return managedObject
}

fun List<RealmObject>.save() {
    val realm = Realm.getDefaultInstance()

    realm.beginTransaction()
    realm.copyToRealmOrUpdate(this)
    realm.commitTransaction()

    realm.close()
}

/**
 * Turns out, not actually that important to know this. Realm objects that aren't
 * managed also return false from `isValid()`, which usually is all we need to know.
 */
fun RealmObject.isManaged() = this is RealmObjectProxy