package net.sigmabeta.chipbox.model.database

import io.realm.Realm
import io.realm.RealmObject
import net.sigmabeta.chipbox.model.IdRealmObject
import java.util.*

inline fun <reified T : RealmObject> T.getNextPrimaryKey(): String {
    val id = UUID.randomUUID()

    return id.toString()
}

inline fun <reified T : RealmObject> T.save(): T {
    val realm = getRealmInstance()

    val managedObject = realm.getFromTransaction {
        if (this@save is IdRealmObject) {
            if (this@save.getPrimaryKey() == null) {
                this@save.setPrimaryKey(getNextPrimaryKey())
            }
        }

        return@getFromTransaction copyToRealm(this@save)
    }

    realm.closeAndReport()

    return managedObject
}

inline fun <T : RealmObject?> Realm.getFromTransaction(func: Realm.() -> T): T {
    val wasInTransactionBefore: Boolean
    if (!isInTransaction) {
        wasInTransactionBefore = true
        beginTransaction()
    } else {
        wasInTransactionBefore = false
    }

    val managedObject = func()

    if (wasInTransactionBefore) {
        commitTransaction()
    }

    return managedObject
}

inline fun Realm.inTransaction(func: Realm.() -> Unit) {
    val wasInTransactionBefore: Boolean
    if (!isInTransaction) {
        wasInTransactionBefore = true
        beginTransaction()
    } else {
        wasInTransactionBefore = false
    }

    func()

    if (wasInTransactionBefore) {
        commitTransaction()
    }
}

fun getRealmInstance(): Realm {
    val realm = Realm.getDefaultInstance()
    val refCount = Realm.getLocalInstanceCount(realm.configuration)
    val threadName = Thread.currentThread().name

//    logInfo("Getting realm instance #$refCount for thread $threadName.")

    return realm
}

fun Realm.closeAndReport() {
    close()
//    val refCount = Realm.getLocalInstanceCount(configuration)
//    val threadName = Thread.currentThread().name
//    logInfo("Closed realm instance. $refCount references remain for thread $threadName.")
}