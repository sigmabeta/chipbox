package net.sigmabeta.chipbox.model.file


import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class Folder(@PrimaryKey open var path: String? = null) : RealmObject()