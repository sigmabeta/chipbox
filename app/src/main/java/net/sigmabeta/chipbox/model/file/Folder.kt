package net.sigmabeta.chipbox.model.file

import com.raizlabs.android.dbflow.annotation.PrimaryKey
import com.raizlabs.android.dbflow.annotation.Table
import com.raizlabs.android.dbflow.annotation.Unique
import com.raizlabs.android.dbflow.sql.language.SQLite
import com.raizlabs.android.dbflow.structure.BaseModel
import net.sigmabeta.chipbox.ChipboxDatabase
import net.sigmabeta.chipbox.model.database.SongDatabaseHelper
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logInfo
import rx.Observable

@Table(database = ChipboxDatabase::class, allFields = true)
class Folder() : BaseModel() {
    constructor(path: String) : this() {
        this.path = path
    }

    @PrimaryKey (autoincrement = true) var id: Long? = null
    @Unique var path: String? = null

    companion object {
        fun getAll(): List<Folder> {
            return SQLite.select()
                    .from(Folder::class.java)
                    .queryList()
        }

        fun addToDatabase(path: String): Observable<Int> {
            return Observable.create {

                if (checkIfContained(path)) {
                    it.onNext(SongDatabaseHelper.ADD_STATUS_EXISTS)
                    it.onCompleted()
                    return@create
                }

                removeContainedEntries(path)

                Folder(path).insert()

                logInfo("[SongDatabaseHelper] Successfully added folder to database.")
                it.onNext(SongDatabaseHelper.ADD_STATUS_GOOD)

                it.onCompleted()
            }
        }

        private fun checkIfContained(newPath: String): Boolean {
            val folders = getAll()

            folders.forEach {
                it.path?.let { oldPath ->
                    if (newPath.contains(oldPath)) {
                        logError("[SongDatabaseHelper] New folder $newPath is contained by a previously added folder: $oldPath")
                        return true
                    }
                }
            }

            return false
        }

        private fun removeContainedEntries(newPath: String) {
            val folders = getAll()

            // Remove any folders from the DB that are contained by the new folder.
            val idsToRemove = mutableListOf<Long>()
            folders.forEach { oldFolder ->
                oldFolder.path?.let { oldPath ->
                    if (oldPath.contains(newPath)) {
                        logInfo("[SongDatabaseHelper] New folder contains a previously added folder: $oldPath")

                        oldFolder.id?.let {
                            idsToRemove.add(it)
                        }
                    }
                }
            }

            if (idsToRemove.isNotEmpty()) {
                val idsString = idsToRemove.joinToString()
                logInfo("[SongDatabaseHelper] Deleting folders with ids: $idsString")

                SQLite.delete().from(Folder::class.java)
                        .where(Folder_Table.id.`in`(idsToRemove))
                        .query()
            }
        }
    }
}