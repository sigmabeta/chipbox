package net.sigmabeta.chipbox.model.domain

import com.raizlabs.android.dbflow.annotation.PrimaryKey
import com.raizlabs.android.dbflow.annotation.Table
import com.raizlabs.android.dbflow.structure.BaseModel
import net.sigmabeta.chipbox.ChipboxDatabase

@Table(database = ChipboxDatabase::class, allFields = true)
class Artist() : BaseModel() {
    constructor(name: String) : this() {
        this.name = name
    }

    @PrimaryKey (autoincrement = true) var id: Long? = null
    var name: String? = null

    companion object {
        val ARTIST_ALL = -1L
    }
}