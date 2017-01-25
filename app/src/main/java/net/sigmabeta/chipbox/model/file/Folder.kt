package net.sigmabeta.chipbox.model.file


import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import net.sigmabeta.chipbox.model.database.getRealmInstance
import net.sigmabeta.chipbox.model.database.inTransaction
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logInfo

open class Folder(@PrimaryKey open var path: String? = null) : RealmObject() {
    companion object {
        fun getAll(): List<Folder> {
            val realm = getRealmInstance()
            val foldersManaged = realm.where(Folder::class.java)
                    .findAll()
            val folders = realm.copyFromRealm(foldersManaged)
            realm.close()
            return folders
        }

        fun checkIfContained(newPath: String): Boolean {
            val folders = getAll()

            folders.forEach {
                it.path?.let { oldPath ->
                    if (newPath.contains(oldPath)) {
                        logError("[Folder] New folder $newPath is contained by a previously added folder: $oldPath")
                        return true
                    }
                }
            }

            return false
        }

        fun removeContainedEntries(newPath: String) {
            val folders = getAll()

            // Remove any folders from the DB that are contained by the new folder.
            val foldersToRemove = mutableListOf<Folder>()
            folders.forEach { oldFolder ->
                oldFolder.path?.let { oldPath ->
                    if (oldPath.contains(newPath)) {
                        logInfo("[Folder] New folder contains a previously added folder: $oldPath")

                        foldersToRemove.add(oldFolder)
                    }
                }
            }

            if (foldersToRemove.isNotEmpty()) {
                val idsString = foldersToRemove.joinToString()
                logInfo("[Folder] Deleting folders with ids: $idsString")

                val realm = getRealmInstance()

                realm.inTransaction {
                    foldersToRemove.forEach {
                        realm.where(Folder::class.java)
                                .equalTo("path", it.path)
                                .findFirst()
                                .deleteFromRealm()
                    }
                }

                realm.close()
            }
        }
    }
}