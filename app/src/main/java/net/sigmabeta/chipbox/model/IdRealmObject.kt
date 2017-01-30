package net.sigmabeta.chipbox.model

interface IdRealmObject {
    fun getPrimaryKey(): String?
    fun setPrimaryKey(id: String)
}