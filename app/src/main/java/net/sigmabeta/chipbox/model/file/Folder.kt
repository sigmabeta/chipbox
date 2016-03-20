package net.sigmabeta.chipbox.model.file

import com.raizlabs.android.dbflow.annotation.PrimaryKey
import com.raizlabs.android.dbflow.annotation.Table
import com.raizlabs.android.dbflow.annotation.Unique
import com.raizlabs.android.dbflow.structure.BaseModel
import net.sigmabeta.chipbox.ChipboxDatabase

@Table(database = ChipboxDatabase::class, allFields = true)
class Folder() : BaseModel() {
    constructor(path: String) : this() {
        this.path = path
    }

    @PrimaryKey (autoincrement = true) var id: Long? = null
    @Unique var path: String? = null
}