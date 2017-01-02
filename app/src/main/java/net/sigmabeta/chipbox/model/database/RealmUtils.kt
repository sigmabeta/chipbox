package net.sigmabeta.chipbox.model.database

import io.realm.Realm
import io.realm.RealmObject
import io.realm.internal.RealmObjectProxy
import rx.Observable

fun RealmObject.save() {
    val realm = Realm.getDefaultInstance()

    realm.beginTransaction()
    realm.copyToRealmOrUpdate(this)
    realm.commitTransaction()

    realm.close()
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

fun <T : RealmObject> Realm.findFirst(clazz: Class<T>, id: Long): Observable<T> = where(clazz)
        .equalTo("id", id)
        .findFirstAsync()
        .asObservable<T>()
        .filter { it.isLoaded }

fun <T : RealmObject> Realm.findAll(clazz: Class<T>) = where(clazz)
        .findAllAsync()
        .asObservable()

fun <T : RealmObject> Realm.findAllSync(clazz: Class<T>) = where(clazz)
        .findAll()